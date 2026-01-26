package com.wfh.drawio.ai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wfh.drawio.ai.model.StreamEvent;
import com.wfh.drawio.model.entity.Diagram;
import com.wfh.drawio.service.DiagramService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import reactor.core.publisher.Sinks;

import java.util.List;

/**
 * @Title: EditDiagramTool
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ai.tools
 * @Date 2025/12/20 20:44
 * @description: 编辑图表工具
 */
@Slf4j
public class EditDiagramTool {

    private final DiagramService diagramService;
    private final String diagramId;
    private final Sinks.Many<StreamEvent> sink;

    public EditDiagramTool(DiagramService diagramService, String diagramId, Sinks.Many<StreamEvent> sink) {
        this.diagramService = diagramService;
        this.diagramId = diagramId;
        this.sink = sink;
    }

    // Jackson ObjectMapper 实例
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        
        IMPORTANT: DO NOT wrap the output in markdown code blocks (e.g. ```json ... ```), return raw JSON string.
        """)
    public ToolResult<String, String> editDiagram(
            @ToolParam(description = "JSON string containing the list of operations to perform on the diagram")
            String requestJson
    ) {
        log.info("=== EditDiagramTool.execute() 开始执行 ===");
        try {
            // 1. 参数清洗
            if (requestJson == null || requestJson.trim().isEmpty()) {
                return ToolResult.error("Request JSON is empty");
            }

            // 清洗 Markdown 代码块标记 (```json)
            if (requestJson.startsWith("```json")) {
                requestJson = requestJson.replace("```json", "").replace("```", "");
            } else if (requestJson.startsWith("```")) {
                requestJson = requestJson.replace("```", "");
            }
            requestJson = requestJson.trim();

            log.info("接收到编辑指令长度: {}", requestJson.length());
            log.debug("编辑指令内容: {}", requestJson);

            // 2. 作用域检查
            if (diagramId == null){
                log.error("错误: diagramId 未绑定");
                return ToolResult.error("System Error: diagramId not bound");
            }

            // 3. 获取并准备数据
            Diagram diagram = diagramService.getById(diagramId);
            if (diagram == null) {
                return ToolResult.error("Diagram not found: " + diagramId);
            }
            String currentXml = diagram.getDiagramCode();
            if (currentXml == null) currentXml = "";

            // 确保是完整的 XML 结构以便进行 DOM 操作
            if (!currentXml.trim().startsWith("<mxfile")) {
                log.info("检测到非标准结构，进行包装...");
                currentXml = DrawioXmlProcessor.wrapWithModel(currentXml);
            }

            // 4. 解析 JSON
            DiagramSchemas.EditDiagramRequest request;
            try {
                request = objectMapper.readValue(requestJson, DiagramSchemas.EditDiagramRequest.class);
            } catch (Exception e) {
                log.error("JSON 解析失败: {}", e.getMessage());
                return ToolResult.error("Invalid JSON format: " + e.getMessage());
            }

            List<DiagramSchemas.EditOperation> operations = request.getOperations();
            if (operations == null || operations.isEmpty()) {
                return ToolResult.error("Operations list cannot be empty");
            }

            // 5. 校验操作参数
            for (DiagramSchemas.EditOperation op : operations) {
                if (op.getType() == null || op.getCellId() == null) {
                    return ToolResult.error("Each operation must have type and cell_id");
                }
                boolean needXml = "update".equals(op.getType()) || "add".equals(op.getType());
                if (needXml && (op.getNewXml() == null || op.getNewXml().trim().isEmpty())) {
                    return ToolResult.error("new_xml is required for " + op.getType() + " operations");
                }
            }

            // 6. 执行操作
            log.info("开始应用 {} 个编辑操作...", operations.size());
            DrawioXmlProcessor.OperationResult result = DrawioXmlProcessor.applyOperations(currentXml, operations);

            if (!result.success) {
                log.error("编辑操作失败: {}", result.errors);
                return ToolResult.error("Failed to apply operations: " + String.join(", ", result.errors));
            }

            // 7. 保存结果
            String savedXml = result.resultXml;
            diagram.setDiagramCode(savedXml);
            diagramService.updateById(diagram);

            // 推送给前端
            log.info("推送更新后的图表...");
            emitResult(savedXml);

            log.info("=== EditDiagramTool.execute() 执行完成 ===");
            return ToolResult.success(
                    requestJson, // 返回原始请求作为 context
                    "Edit operations applied successfully:\n" +
                            String.join("\n", result.appliedOperations) +
                            (result.errors.isEmpty() ? "" : "\nErrors: " + String.join(", ", result.errors))
            );

        } catch (Exception e) {
            log.error("编辑图表系统异常: ", e);
            return ToolResult.error("Failed to edit diagram: " + e.getMessage());
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