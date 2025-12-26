package com.wfh.drawio.ai.tools;

import com.wfh.drawio.ai.utils.DiagramContextUtil;
import com.wfh.drawio.model.entity.Diagram;
import com.wfh.drawio.service.DiagramService;
import jakarta.annotation.Resource;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * @Title: AppendDiagramTool
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ai.tools
 * @Date 2025/12/20 20:45
 * @description: 追加图表工具
 */
@Component
public class AppendDiagramTool  {


    @Resource
    private DiagramService diagramService;

    public AppendDiagramTool() {}

    @Tool(name = "append_diagram", description = """
        Continue generating diagram XML when previous create_diagram output was truncated due to length limits.

        WHEN TO USE: Only call this tool after create_diagram was truncated (you'll see an error about truncation).

        CRITICAL INSTRUCTIONS:
        1. Do NOT include any wrapper tags - just continue the mxCell elements
        2. Continue from EXACTLY where your previous output stopped
        3. Complete the remaining mxCell elements
        4. If still truncated, call append_diagram again with the next fragment
        """)
    public ToolResult<DiagramSchemas.AppendDiagramRequest, String> appendDiagram(
            @ToolParam(description = "The XML fragment to append") DiagramSchemas.AppendDiagramRequest request
    ) {
        try {
            // 判断是否绑定了作用域
            String diagramId = DiagramContextUtil.getConversationId();
            if (diagramId == null){
                return ToolResult.error("System Error: ThreadLocal not bound");
            }
            // 当前的图表ID
            // 4. 【关键】在后端内部获取 currentXml
            Diagram diagram = diagramService.getById(diagramId);
            String currentXml = diagram.getDiagramCode();

            String xmlFragment = request.getXml();

            // Validation
            if (xmlFragment.contains("UPDATE") || xmlFragment.contains("cell_id") || xmlFragment.contains("operations")) {
                return ToolResult.error("Invalid fragment: contains edit operation markers");
            }

            // Validate XML fragment
            DrawioXmlProcessor.ValidationResult validation =
                    DrawioXmlProcessor.validateAndParseXml("<wrapper>" + xmlFragment + "</wrapper>");

            if (!validation.valid) {
                return ToolResult.error("XML fragment validation failed: " + validation.error);
            }
            String finalXml = "";
            // 核心修改逻辑：判断是否是完整 XML，决定插入位置
            if (currentXml.trim().startsWith("<mxfile")) {
                // 如果是完整 XML，需要插入到 </root> 之前
                int rootEndIndex = currentXml.lastIndexOf("</root>");
                if (rootEndIndex != -1) {
                    // 拆分：头部 + 新片段 + 尾部
                    String beforeRootEnd = currentXml.substring(0, rootEndIndex);
                    String afterRootEnd = currentXml.substring(rootEndIndex);
                    finalXml = beforeRootEnd + "\n" + xmlFragment + "\n" + afterRootEnd;
                } else {
                    // 异常情况：有 mxfile 头但没 root 尾？直接硬追加或报错
                    // 这里选择兜底策略：硬追加，虽然可能无效
                    finalXml = currentXml + "\n" + xmlFragment;
                }
            } else {
                // 如果数据库里存的是残缺片段（历史遗留或还没包装），直接追加
                String tempXml = currentXml + "\n" + xmlFragment;
                // 然后顺手把它包装成标准的
                finalXml = DrawioXmlProcessor.wrapWithModel(tempXml);
            }

            // 5. 【关键】把拼接好的结果保存到数据库中去
            diagram.setDiagramCode(finalXml);
            diagramService.updateById(diagram);
            // 推送给前端渲染
            DiagramContextUtil.result(finalXml);
            return ToolResult.success(
                    "XML fragment appended successfully.",
                    "XML fragment appended successfully. Total cells: " + DrawioXmlProcessor.extractMxCells(finalXml).size()
            );

        } catch (Exception e) {
            return ToolResult.error("Failed to append diagram: " + e.getMessage());
        }
    }


}