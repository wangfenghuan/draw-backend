package com.wfh.drawio.ai.tools;

import com.wfh.drawio.ai.model.StreamEvent;
import com.wfh.drawio.model.entity.Diagram;
import com.wfh.drawio.service.DiagramService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import reactor.core.publisher.Sinks;

/**
 * @Title: CreateDiagramTool
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ai.tools
 * @Date 2025/12/20 20:44
 * @description: 创建图表工具
 */
@Slf4j
public class CreateDiagramTool {

    private final DiagramService diagramService;
    private final String diagramId;
    private final Sinks.Many<StreamEvent> sink;

    public CreateDiagramTool(DiagramService diagramService, String diagramId, Sinks.Many<StreamEvent> sink) {
        this.diagramService = diagramService;
        this.diagramId = diagramId;
        this.sink = sink;
    }

    @Tool(name = "display_diagram", description = """
        Create a new diagram on draw.io. Pass ONLY the mxCell elements - wrapper tags and root cells are added automatically.

        VALIDATION RULES (XML will be rejected if violated):
        1. Generate ONLY mxCell elements - NO wrapper tags (<mxfile>, <mxGraphModel>, <root>)
        2. Do NOT include root cells (id="0" or id="1") - they are added automatically
        3. All mxCell elements must be siblings - never nested
        4. Every mxCell needs a unique id (start from "2")
        5. Every mxCell needs a valid parent attribute (use "1" for top-level)
        6. Escape special chars in values: &lt; &gt; &amp; &quot;
        7. DO NOT wrap the output in markdown code blocks (e.g. ```xml ... ```), return raw string.

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
    public ToolResult<String, String> displayDiagram(
            @ToolParam(description = "The generated XML string containing ONLY mxCell elements")
            String xml
    ) {
        log.info("=== CreateDiagramTool.execute() 开始执行 ===");
        try {
            // 旁路日志
            logInternal("[display_diagram]创建图表:");
            log.info("[display_diagram]创建图表开始");

            if (xml == null) {
                log.error("错误: 参数 xml 为 null");
                return ToolResult.error("Parameter 'xml' is null.");
            }

            // 当前的图表ID
            log.info("获取到 diagramId: {}", diagramId);

            // 清洗 Markdown 代码块标记
            if (xml.startsWith("```xml")) {
                xml = xml.replace("```xml", "").replace("```", "");
            } else if (xml.startsWith("```")) {
                xml = xml.replace("```", "");
            }
            xml = xml.trim();

            log.info("接收到 XML 长度: {}", xml.length());
            log.debug("XML 内容: {}", xml);

            // 1. 基础防错校验
            if (xml.isEmpty()) {
                log.error("错误: XML 内容为空");
                return ToolResult.error("Invalid XML content: empty");
            }

            // 2. 检查 AI 是否混淆了工具 (使用了 Edit 的指令)
            if (xml.contains("UPDATE") || xml.contains("cell_id") || xml.contains("operations")) {
                log.error("错误: XML 包含编辑操作标记");
                return ToolResult.error("Invalid XML: contains edit operation markers. Did you mean to use edit_diagram?");
            }

            // 3. 校验 XML 结构
            // 在校验时手动包一层 <root>
            String wrappedXmlForValidation = "<root>" + xml + "</root>";
            log.info("开始验证 XML 结构...");

            DrawioXmlProcessor.ValidationResult validation = DrawioXmlProcessor.validateAndParseXml(wrappedXmlForValidation);

            if (!validation.valid) {
                log.error("错误: XML 验证失败 - {}", validation.error);
                return ToolResult.error("XML validation failed: " + validation.error);
            }
            log.info("XML 验证通过");

            // 4. 检查是否包含了不该有的 Wrapper
            // 如果 AI 还是生成了 <mxGraphModel>，我们可以在这里做一些清洗，或者报错
            // 这里做一个简单的检查：确保至少包含 <mxCell
            if (!xml.contains("<mxCell")) {
                log.error("错误: XML 中不包含 <mxCell> 元素");
                return ToolResult.error("Invalid XML content: must contain <mxCell> elements");
            }
            log.info("开始包装 XML...");
            String fullXml = DrawioXmlProcessor.wrapWithModel(xml);
            log.info("XML 包装完成，长度: {}", fullXml.length());

            // 当前图表生成完毕，保存到对应的数据库表中(这里先把数据库中的图表查出来)
            log.info("开始查询数据库，diagramId: {}", diagramId);
            Diagram diagram = diagramService.getById(diagramId);
            log.info("查询图表记录: diagramId={}, 结果={}", diagramId, diagram == null ? "NULL" : "找到");
            if (diagram == null) {
                log.error("错误: 数据库中未找到 diagramId={} 的记录", diagramId);
                return ToolResult.error("Diagram not found: " + diagramId);
            }

            log.info("开始更新 diagramCode...");
            diagram.setDiagramCode(fullXml);
            diagramService.updateById(diagram);
            log.info("数据库更新完成");

            // 直接把结果推给前端渲染
            log.info("推送结果到前端...");
            emitResult(fullXml);
            logInternal("diagram generated");

            log.info("=== CreateDiagramTool.execute() 执行完成 ===");
            return ToolResult.success(xml,
                    "Diagram created successfully. " + extractMxCellCount(xml) + " cells created.");

        } catch (Exception e) {
            log.error("执行异常: ", e);
            log.error("异常详情: {}", e.getMessage());
            return ToolResult.error("Failed to create diagram: " + e.getMessage());
        }
    }

    private void logInternal(String message) {
        if (sink != null) {
            sink.tryEmitNext(StreamEvent.builder()
                    .type("too_call")
                    .content(message)
                    .build());
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

    private int extractMxCellCount(String xml) {
        return DrawioXmlProcessor.extractMxCells(xml).size();
    }

}