package com.wfh.drawio.ai.tools;

import com.wfh.drawio.ai.model.StreamEvent;
import com.wfh.drawio.model.entity.Diagram;
import com.wfh.drawio.service.DiagramService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import reactor.core.publisher.Sinks;

/**
 * @Title: AppendDiagramTool
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ai.tools
 * @Date 2025/12/20 20:45
 * @description: 追加图表工具
 */
@Slf4j
public class AppendDiagramTool {

    private final DiagramService diagramService;
    private final String diagramId;
    private final Sinks.Many<StreamEvent> sink;

    public AppendDiagramTool(DiagramService diagramService, String diagramId, Sinks.Many<StreamEvent> sink) {
        this.diagramService = diagramService;
        this.diagramId = diagramId;
        this.sink = sink;
    }

    @Tool(name = "append_diagram", description = """
        Continue generating diagram XML when previous create_diagram output was truncated due to length limits.

        WHEN TO USE: Only call this tool after create_diagram was truncated (you'll see an error about truncation).

        CRITICAL INSTRUCTIONS:
        1. Do NOT include any wrapper tags - just continue the mxCell elements
        2. Continue from EXACTLY where your previous output stopped
        3. Complete the remaining mxCell elements
        4. If still truncated, call append_diagram again with the next fragment
        5. DO NOT wrap the output in markdown code blocks (e.g. ```xml ... ```), return raw string.
        """)
    public ToolResult<String, String> appendDiagram(
            @ToolParam(description = "The XML fragment to append (ONLY mxCell elements)")
            String xmlFragment
    ) {
        log.info("=== AppendDiagramTool.execute() 开始执行 ===");
        try {
            // 1. 参数校验与清洗
            if (xmlFragment == null) {
                return ToolResult.error("Parameter 'xmlFragment' is null.");
            }

            // 清洗 Markdown 代码块标记
            if (xmlFragment.startsWith("```xml")) {
                xmlFragment = xmlFragment.replace("```xml", "").replace("```", "");
            } else if (xmlFragment.startsWith("```")) {
                xmlFragment = xmlFragment.replace("```", "");
            }
            xmlFragment = xmlFragment.trim();

            log.info("接收到追加片段长度: {}", xmlFragment.length());

            if (xmlFragment.isEmpty()) {
                return ToolResult.error("XML fragment is empty.");
            }

            // 2. 判断是否绑定了作用域
            if (diagramId == null){
                log.error("错误: diagramId 未绑定");
                return ToolResult.error("System Error: diagramId not bound");
            }

            // 3. 在后端内部获取 currentXml
            Diagram diagram = diagramService.getById(diagramId);
            if (diagram == null) {
                return ToolResult.error("Diagram not found: " + diagramId);
            }
            String currentXml = diagram.getDiagramCode();
            if (currentXml == null) {
                currentXml = "";
            }

            // 4. 业务规则校验
            if (xmlFragment.contains("UPDATE") || xmlFragment.contains("cell_id") || xmlFragment.contains("operations")) {
                return ToolResult.error("Invalid fragment: contains edit operation markers");
            }

            // 5. XML 结构校验 (包裹一层 wrapper 进行校验)
            DrawioXmlProcessor.ValidationResult validation =
                    DrawioXmlProcessor.validateAndParseXml("<wrapper>" + xmlFragment + "</wrapper>");

            if (!validation.valid) {
                log.error("追加片段校验失败: {}", validation.error);
                return ToolResult.error("XML fragment validation failed: " + validation.error);
            }

            String finalXml = "";
            // 6. 核心拼接逻辑：判断是否是完整 XML，决定插入位置
            if (currentXml.trim().startsWith("<mxfile")) {
                // 如果是完整 XML，需要插入到 </root> 之前
                int rootEndIndex = currentXml.lastIndexOf("</root>");
                if (rootEndIndex != -1) {
                    // 拆分：头部 + 新片段 + 尾部
                    String beforeRootEnd = currentXml.substring(0, rootEndIndex);
                    String afterRootEnd = currentXml.substring(rootEndIndex);
                    finalXml = beforeRootEnd + "\n" + xmlFragment + "\n" + afterRootEnd;
                } else {
                    // 异常情况：有 mxfile 头但没 root 尾？直接硬追加作为兜底
                    finalXml = currentXml + "\n" + xmlFragment;
                }
            } else {
                // 如果数据库里存的是残缺片段（历史遗留或还没包装），直接追加
                String tempXml = currentXml + "\n" + xmlFragment;
                // 然后顺手把它包装成标准的
                finalXml = DrawioXmlProcessor.wrapWithModel(tempXml);
            }

            // 7. 保存结果
            log.info("拼接完成，更新数据库...");
            diagram.setDiagramCode(finalXml);
            diagramService.updateById(diagram);

            // 推送给前端渲染
            emitResult(finalXml);

            log.info("=== AppendDiagramTool.execute() 执行完成 ===");
            return ToolResult.success(
                    xmlFragment,
                    "XML fragment appended successfully. Total cells: " + DrawioXmlProcessor.extractMxCells(finalXml).size()
            );

        } catch (Exception e) {
            log.error("追加图表异常: ", e);
            return ToolResult.error("Failed to append diagram: " + e.getMessage());
        }
    }

    private void emitResult(Object data) {
         if (sink != null) {
            sink.tryEmitNext(StreamEvent.builder()
                    .type("tool_call_result")
                    .content(data)
                    .build());
        }
    }
}