package com.wfh.drawio.ai.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * @Title: CreateDiagramTool
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ai.tools
 * @Date 2025/12/20 20:44
 * @description: 创建图表工具
 */
@Component
public class CreateDiagramTool {

    public CreateDiagramTool() {}

    @Tool(name = "display_diagram", description = """
        Create a new diagram on draw.io. Pass ONLY the mxCell elements - wrapper tags and root cells are added automatically.

        VALIDATION RULES (XML will be rejected if violated):
        1. Generate ONLY mxCell elements - NO wrapper tags (<mxfile>, <mxGraphModel>, <root>)
        2. Do NOT include root cells (id="0" or id="1") - they are added automatically
        3. All mxCell elements must be siblings - never nested
        4. Every mxCell needs a unique id (start from "2")
        5. Every mxCell needs a valid parent attribute (use "1" for top-level)
        6. Escape special chars in values: &lt; &gt; &amp; &quot;

        Example (generate ONLY this - no wrapper tags):
        <mxCell id="lane1" value="Frontend" style="swimlane;" vertex="1" parent="1">
          <mxGeometry x="40" y="40" width="200" height="200" as="geometry"/>
        </mxCell>
        <mxCell id="step1" value="Step 1" style="rounded=1;" vertex="1" parent="lane1">
          <mxGeometry x="20" y="60" width="160" height="40" as="geometry"/>
        </mxCell>

        Notes:
        - For AWS diagrams, use AWS 2025 icons.
        - For animated connectors, add "flowAnimation=1" to edge style.
        """)
    public ToolResult<DiagramSchemas.CreateDiagramRequest, String> execute(
            @ToolParam(description = "The request object containing the generated XML string with mxCell elements")
            DiagramSchemas.CreateDiagramRequest request
    ) {
        try {
            String xml = request.getXml();

            // 1. 基础防错校验
            if (xml == null || xml.trim().isEmpty()) {
                return ToolResult.error("Invalid XML content: empty");
            }

            // 2. 检查 AI 是否混淆了工具 (使用了 Edit 的指令)
            if (xml.contains("UPDATE") || xml.contains("cell_id") || xml.contains("operations")) {
                return ToolResult.error("Invalid XML: contains edit operation markers. Did you mean to use edit_diagram?");
            }

            // 3. 校验 XML 结构
            // 在校验时手动包一层 <root>
            String wrappedXmlForValidation = "<root>" + xml + "</root>";

            DrawioXmlProcessor.ValidationResult validation = DrawioXmlProcessor.validateAndParseXml(wrappedXmlForValidation);

            if (!validation.valid) {
                return ToolResult.error("XML validation failed: " + validation.error);
            }

            // 4. 检查是否包含了不该有的 Wrapper
            // 如果 AI 还是生成了 <mxGraphModel>，我们可以在这里做一些清洗，或者报错
            // 这里做一个简单的检查：确保至少包含 <mxCell
            if (!xml.contains("<mxCell")) {
                return ToolResult.error("Invalid XML content: must contain <mxCell> elements");
            }

            return ToolResult.success(xml,
                    "Diagram created successfully. " + extractMxCellCount(xml) + " cells created.");

        } catch (Exception e) {
            return ToolResult.error("Failed to create diagram: " + e.getMessage());
        }
    }

    private int extractMxCellCount(String xml) {
        return DrawioXmlProcessor.extractMxCells(xml).size();
    }

}
