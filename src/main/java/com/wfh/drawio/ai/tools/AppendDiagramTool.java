package com.wfh.drawio.ai.tools;

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
public class AppendDiagramTool {

    public AppendDiagramTool() {}

    @Tool(description = """
        Continue generating diagram XML when previous create_diagram output was truncated due to length limits.

        WHEN TO USE: Only call this tool after create_diagram was truncated (you'll see an error about truncation).

        CRITICAL INSTRUCTIONS:
        1. Do NOT include any wrapper tags - just continue the mxCell elements
        2. Continue from EXACTLY where your previous output stopped
        3. Complete the remaining mxCell elements
        4. If still truncated, call append_diagram again with the next fragment
        """)
    public ToolResult<DiagramSchemas.AppendDiagramRequest, String> execute(
            // 3. 去掉 currentXml 参数，AI 不需要也不应该传这个
            @ToolParam(description = "The XML fragment to append") DiagramSchemas.AppendDiagramRequest request
    ) {
        try {
            // 4. 【关键】在后端内部获取 currentXml
            String currentXml = getMockCurrentXml();

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

            // Append to current XML
            String appendedXml = currentXml + "\n" + xmlFragment;

            // 5. 【关键】把拼接好的结果保存回去
            // diagramService.saveDraftXml(userId, appendedXml);

            return ToolResult.success(
                    appendedXml,
                    "XML fragment appended successfully. Total cells: " + DrawioXmlProcessor.extractMxCells(appendedXml).size()
            );

        } catch (Exception e) {
            return ToolResult.error("Failed to append diagram: " + e.getMessage());
        }
    }

    // 模拟获取当前 XML 的方法
    private String getMockCurrentXml() {
        // 在真实业务中，这里不要写死，要去查库
        return "<mxGraphModel><root><mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>";
    }

}
