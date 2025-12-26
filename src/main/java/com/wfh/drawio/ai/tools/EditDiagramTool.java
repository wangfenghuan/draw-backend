package com.wfh.drawio.ai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wfh.drawio.ai.utils.DiagramContextUtil;
import com.wfh.drawio.model.entity.Diagram;
import com.wfh.drawio.service.DiagramService;
import jakarta.annotation.Resource;
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

    @Resource
    private DiagramService diagramService;

    @Tool(name = "edit_diagram", description = """
        Edit the current diagram by ID-based operations (update/add/delete cells).

        Pass a JSON string with this structure:
        {
          "operations": [
            {
              "type": "update|add|delete",
              "cell_id": "cellId",
              "new_xml": "complete mxCell element (required for update/add)"
            }
          ]
        }

        Operations:
        - update: Replace an existing cell by its id. Provide cell_id and complete new_xml.
        - add: Add a new cell. Provide cell_id (new unique id) and new_xml.
        - delete: Remove a cell by its id. Only cell_id is needed.

        For update/add, new_xml must be a complete mxCell element including mxGeometry.

        Example JSON:
        {
          "operations": [
            {
              "type": "update",
              "cell_id": "5",
              "new_xml": "<mxCell id=\\"5\\" value=\\"Label\\"><mxGeometry x=\\"0\\" y=\\"0\\" width=\\"120\\" height=\\"60\\" as=\\"geometry\\"/></mxCell>"
            }
          ]
        }
        """)
    public ToolResult<DiagramSchemas.EditDiagramRequest, String> editDiagram(
            @ToolParam(description = "JSON string containing the list of operations to perform on the diagram")
            String requestJson
    ) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            // 判断是否绑定了作用域
            String diagramId = DiagramContextUtil.getConversationId();
            if (diagramId == null){
                return ToolResult.error("System Error: ThreadLocal not bound");
            }
            Diagram diagram = diagramService.getById(diagramId);
            String currentXml = diagram.getDiagramCode();

            // Wrap mxCell fragments into complete drawio XML structure if needed
            if (!currentXml.trim().startsWith("<mxfile")) {
                currentXml = DrawioXmlProcessor.wrapWithModel(currentXml);
            }

            // Parse JSON string to EditDiagramRequest object
            DiagramSchemas.EditDiagramRequest request = objectMapper.readValue(requestJson, DiagramSchemas.EditDiagramRequest.class);
            List<DiagramSchemas.EditOperation> operations = request.getOperations();

            if (operations == null || operations.isEmpty()) {
                return ToolResult.error("Operations list cannot be empty");
            }

            // Validate each operation
            for (DiagramSchemas.EditOperation op : operations) {
                if (op.getType() == null || op.getCellId() == null) {
                    return ToolResult.error("Each operation must have type and cell_id");
                }
                boolean res = ("update".equals(op.getType()) || "add".equals(op.getType())) &&
                        (op.getNewXml() == null || op.getNewXml().trim().isEmpty());
                if (res) {
                    return ToolResult.error("new_xml is required for " + op.getType() + " operations");
                }
            }
            // Apply operations
            DrawioXmlProcessor.OperationResult result = DrawioXmlProcessor.applyOperations(currentXml, operations);

            if (!result.success) {
                return ToolResult.error("Failed to apply operations: " + String.join(", ", result.errors));
            }

            // 3. 操作成功后，保存新的 XML 到数据库
            // Extract mxCell elements only (similar to CreateDiagramTool behavior)
            String savedXml = result.resultXml;
            diagram.setDiagramCode(savedXml);
            diagramService.updateById(diagram);
            // 推送给前端，完整的xml
            DiagramContextUtil.result(savedXml);
            return ToolResult.success(
                    "updated",
                    "Edit operations applied successfully:\n" +
                            String.join("\n", result.appliedOperations) +
                            (result.errors.isEmpty() ? "" : "\nErrors: " + String.join(", ", result.errors))
            );

        } catch (Exception e) {
            if (e instanceof com.fasterxml.jackson.core.JsonProcessingException) {
                return ToolResult.error("Invalid JSON format: " + e.getMessage());
            }
            return ToolResult.error("Failed to edit diagram: " + e.getMessage());
        }
    }


}