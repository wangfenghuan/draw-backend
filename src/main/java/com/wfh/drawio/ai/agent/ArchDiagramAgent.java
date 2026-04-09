package com.wfh.drawio.ai.agent;

import cn.hutool.json.JSONUtil;
import com.wfh.drawio.ai.advisor.MyLoggerAdvisor;
import com.wfh.drawio.ai.config.MultiModelFactory;
import com.wfh.drawio.ai.model.StreamEvent;
import com.wfh.drawio.ai.tools.CreateDiagramTool;
import com.wfh.drawio.ai.utils.PromptUtil;
import com.wfh.drawio.service.DiagramService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Objects;

/**
 * @Title: ArchDiagramAgent
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ai.agent
 * @Date 2026/4/9 17:17
 * @description: 架构图生成 Agent，接收 YAML 架构摘要并生成 draw.io 可识别的项目架构图
 */
@Component
@Slf4j
public class ArchDiagramAgent {

    @Resource
    private MultiModelFactory multiModelFactory;

    @Resource
    private DiagramService diagramService;

    @Value("${spring.ai.openai.chat.options.model}")
    private String defaultModelId;

    private static final String ARCH_DIAGRAM_PROMPT = """
            你是一名架构图绘制专家。你将收到一份 YAML 格式的项目架构摘要，需要将其转换为 draw.io 架构图。

            ## 任务目标
            根据输入的 YAML 架构摘要，生成清晰的分层架构图，展示：
            1. 各层组件（Web层、服务层、缓存层、持久层等）
            2. 中间件依赖关系
            3. 核心流转路径
            4. 设计策略标注

            ## 架构图布局规则
            - **分层布局**: 采用垂直分层，Web层在顶部，持久层在底部
            - **组件分组**: 使用 swimlane 容器将同层组件归类
            - **颜色方案**:
              - Web层: 蓝色 (#E1E8EE / #4B7BEC)
              - 服务层: 紫色 (#F3E5F5 / #8854D0)
              - 缓存层: 橙色 (#FFF3E0 / #FA8231)
              - 持久层: 绿色 (#E8F5E9 / #20BF6B)
              - 中间件: 使用对应颜色标注
            - **连线规则**: 使用 orthogonalEdgeStyle，标注流转方向和数据类型

            ## 坐标规划 (示例)
            - Web层泳道: x=50, y=40, width=400
            - 服务层泳道: x=50, y=200, width=400
            - 缓存层泳道: x=500, y=40, width=200
            - 持久层泳道: x=500, y=200, width=200
            - 中间件区域: x=750, y=40, width=200

            请根据输入的 YAML 数据，调用 display_diagram 工具生成架构图。
            """;

    /**
     * 创建带有架构图生成工具的 ChatClient
     *
     * @param modelId   模型 ID
     * @param diagramId 图表 ID（用于保存生成的架构图）
     * @param sink      旁路管道（用于推送工具执行日志）
     * @return ChatClient
     */
    private ChatClient createChatClient(String modelId, String diagramId, Sinks.Many<StreamEvent> sink) {
        String targetModelId = (modelId == null || modelId.isEmpty()) ? defaultModelId : modelId;
        ChatModel chatModel = multiModelFactory.getChatModel(targetModelId);

        // 实例化带有上下文的创建图表工具
        CreateDiagramTool createTool = new CreateDiagramTool(diagramService, diagramId, sink);

        // 加载 draw.io XML 生成指南
        ClassPathResource xmlGuide = new ClassPathResource("/doc/xml_guide.md");

        return ChatClient.builder(chatModel)
                .defaultTools(createTool)
                .defaultSystem(xmlGuide)
                .defaultSystem(PromptUtil.getSystemPrompt(targetModelId, true))
                .defaultSystem(ARCH_DIAGRAM_PROMPT)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
    }

    /**
     * 流式生成架构图
     *
     * @param modelId      模型 ID（可选，为空时使用默认模型）
     * @param diagramId    图表 ID（用于保存生成的架构图）
     * @param yamlSummary  YAML 格式的架构摘要（来自 AstSummaryAgent）
     * @return 流式响应，返回 JSON 格式的 StreamEvent
     */
    public Flux<String> genArchDiagram(String modelId, String diagramId, String yamlSummary) {
        // 创建旁路管道（用于推送工具执行日志）
        Sinks.Many<StreamEvent> sideChannelSink = Sinks.many().unicast().onBackpressureBuffer();

        ChatClient chatClient = createChatClient(modelId, diagramId, sideChannelSink);

        String userPrompt = "请根据以下 YAML 架构摘要生成项目架构图：\n\n" + yamlSummary;

        // 工具执行日志流
        Flux<String> toolLogFlux = sideChannelSink.asFlux()
                .map(JSONUtil::toJsonStr);

        // AI 主回复流
        Flux<String> aiResFlux = chatClient.prompt()
                .user(userPrompt)
                .stream()
                .content()
                .filter(Objects::nonNull)
                .map(text -> JSONUtil.toJsonStr(StreamEvent.builder()
                        .type("text")
                        .content(text)
                        .build()))
                .doOnTerminate(sideChannelSink::tryEmitComplete)
                .doOnError(e -> log.error("架构图生成流式响应异常: {}", e.getMessage()));

        // 合并流
        return Flux.merge(toolLogFlux, aiResFlux);
    }

    /**
     * 非流式生成架构图（同步模式）
     *
     * @param modelId     模型 ID（可选）
     * @param diagramId   图表 ID
     * @param yamlSummary YAML 架构摘要
     * @return 生成的架构图 XML
     */
    public String genArchDiagramSync(String modelId, String diagramId, String yamlSummary) {
        ChatClient chatClient = createChatClient(modelId, diagramId, null);

        String userPrompt = "请根据以下 YAML 架构摘要生成项目架构图：\n\n" + yamlSummary;

        String result = chatClient.prompt()
                .user(userPrompt)
                .call()
                .content();

        log.info("架构图生成完成，结果长度: {} 字符", result != null ? result.length() : 0);
        return result;
    }
}