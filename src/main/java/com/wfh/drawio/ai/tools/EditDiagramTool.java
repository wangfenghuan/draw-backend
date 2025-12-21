package com.wfh.drawio.ai.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Title: EditDiagramTool
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ai.tools
 * @Date 2025/12/20 20:44
 * @description: 编辑图表工具
 */
@Component
public class EditDiagramTool {

    public EditDiagramTool() {}

    @Tool(name = "edit_diagram", description = """
        Edit the current diagram by ID-based operations (update/add/delete cells).

        Operations:
        - update: Replace an existing cell by its id. Provide cell_id and complete new_xml.
        - add: Add a new cell. Provide cell_id (new unique id) and new_xml.
        - delete: Remove a cell by its id. Only cell_id is needed.

        For update/add, new_xml must be a complete mxCell element including mxGeometry.

        ⚠️ JSON ESCAPING: Every " inside new_xml MUST be escaped as \\".
        Example: id=\\"5\\" value=\\"Label\\"
        """)
    public ToolResult<DiagramSchemas.EditDiagramRequest, String> execute(
            // 1. 去掉了 currentXml 参数，AI 不需要传这个
            @ToolParam(description = "The list of operations to perform on the diagram")
            DiagramSchemas.EditDiagramRequest request
    ) {
        try {
            // 2. 在后端内部获取 currentXml
            // 实际开发中，这里应该从 Session、Database 或 ThreadLocal 中获取当前用户的图表
            // String currentXml = diagramService.getCurrentXml(userId);
            String currentXml = getMockCurrentXml(); // 临时模拟，防止编译报错

            List<DiagramSchemas.EditOperation> operations = request.getOperations();

            if (operations == null || operations.isEmpty()) {
                return ToolResult.error("Operations list cannot be empty");
            }

            // Validate each operation
            for (DiagramSchemas.EditOperation op : operations) {
                if (op.getType() == null || op.getCellId() == null) {
                    return ToolResult.error("Each operation must have type and cell_id");
                }

                if ((op.getType().equals("update") || op.getType().equals("add")) &&
                        (op.getNewXml() == null || op.getNewXml().trim().isEmpty())) {
                    return ToolResult.error("new_xml is required for " + op.getType() + " operations");
                }
            }

            // Apply operations
            DrawioXmlProcessor.OperationResult result = DrawioXmlProcessor.applyOperations(currentXml, operations);

            if (!result.success) {
                return ToolResult.error("Failed to apply operations: " + String.join(", ", result.errors));
            }

            // 3. 【关键】操作成功后，记得保存新的 XML 状态
            // diagramService.saveXml(userId, result.resultXml);

            return ToolResult.success(
                    result.resultXml,
                    "Edit operations applied successfully:\n" +
                            String.join("\n", result.appliedOperations) +
                            (result.errors.isEmpty() ? "" : "\nErrors: " + String.join(", ", result.errors))
            );

        } catch (Exception e) {
            return ToolResult.error("Failed to edit diagram: " + e.getMessage());
        }
    }

    // 模拟获取数据的方法
    private String getMockCurrentXml() {
        return "<mxGraphModel><root><mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/></root></mxGraphModel>";
    }

}
