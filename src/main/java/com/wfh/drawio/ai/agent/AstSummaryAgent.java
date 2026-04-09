package com.wfh.drawio.ai.agent;

import cn.hutool.json.JSONUtil;
import com.wfh.drawio.ai.config.MultiModelFactory;
import com.wfh.drawio.ai.model.StreamEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Objects;

/**
 * @Title: AstSummaryAgent
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ai.agent
 * @Date 2026/4/9 17:17
 * @description: AST 架构分析 Agent，基于抽象语法树分析项目架构并生成 YAML 格式的架构描述
 */
@Component
@Slf4j
public class AstSummaryAgent {

    @Resource
    private MultiModelFactory multiModelFactory;

    @Value("${spring.ai.openai.chat.options.model}")
    private String defaultModelId;

    private static final String SYSTEM_PROMPT = """
            你是一名资深后端架构师。我将为你提供一份 Spring Boot 项目的类名、注解及依赖关系。请忽略具体的业务代码逻辑，从宏观视角对项目进行架构审计，并以 YAML 格式输出结果。

            任务要求：

            1. 分层归类 (logical_layers): 将所有类聚类为以下层次：
               - web_layer: Controller、Handler、Interceptor 等 Web 层组件
               - cache_layer: Cache、Redis 相关组件
               - service_layer: Service、Manager、Scheduler 等业务逻辑层
               - mq_layer: MQ、Message、Queue 相关组件（如有）
               - persistence_layer: Mapper、Repository、Entity、DTO、VO 等持久层

            2. 核心流转 (architecture_flow): 描述请求从 Controller 到数据库的典型流转路径，用箭头表示调用链。

            3. 中间件识别 (middleware): 根据依赖识别系统使用的基础设施，如 Redis、MySQL、PostgreSQL、MinIO 等。

            4. 策略推导 (design_strategies): 根据类名和依赖推断架构意图，例如：
               - Caffeine + Redis 表示多级缓存策略
               - Redisson 表示分布式锁/限流策略
               - WebSocket 表示实时协作架构

            5. 核心优化策略 (optimization_strategies): 总结 3-5 条关键优化策略，如异步解耦、削峰填谷、热点数据识别等。

            输出格式示例：
            ```yaml
            logical_layers:
              web_layer: [类名列表]
              cache_layer: [类名列表]
              service_layer: [类名列表]
              persistence_layer: [类名列表]

            architecture_flow: "Controller -> Service -> Mapper -> Database"

            middleware:
              - Redis (缓存/分布式锁)
              - MySQL (主数据库)
              - PostgreSQL (向量数据库)
              - MinIO (对象存储)

            design_strategies:
              - 多级缓存: Caffeine 本地缓存 + Redis 分布式缓存
              - 分布式限流: 基于 Redisson RateLimiter

            optimization_strategies:
              - 异步解耦: WebSocket 实时协作
              - 削峰填谷: 分布式限流保护核心接口
              - 热点数据识别: 多级缓存策略
            ```

            限制：严禁输出任何具体的方法名（如 saveUser()）或局部变量，只关注宏观架构层面。
            """;

    /**
     * 流式生成架构分析摘要
     *
     * @param modelId 模型 ID（可选，为空时使用默认模型）
     * @param astData AST 抽象语法树数据（类名、注解、依赖关系）
     * @return 流式响应，返回 JSON 格式的 StreamEvent
     */
    public Flux<String> genSummary(String modelId, String astData) {
        String targetModelId = (modelId == null || modelId.isEmpty()) ? defaultModelId : modelId;
        ChatModel chatModel = multiModelFactory.getChatModel(targetModelId);

        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .build();

        String userPrompt = "请根据下面的 AST 抽象语法树数据，分析该项目的架构并生成架构描述：\n\n" + astData;

        return chatClient.prompt()
                .user(userPrompt)
                .stream()
                .content()
                .filter(Objects::nonNull)
                .map(text -> JSONUtil.toJsonStr(StreamEvent.builder()
                        .type("text")
                        .content(text)
                        .build()))
                .doOnError(e -> log.error("架构分析流式响应异常: {}", e.getMessage()));
    }

    /**
     * 非流式生成架构分析摘要（适用于小规模数据或需要完整结果的场景）
     *
     * @param modelId 模型 ID（可选，为空时使用默认模型）
     * @param astData AST 抽象语法树数据
     * @return 完整的 YAML 格式架构分析结果
     */
    public String genSummarySync(String modelId, String astData) {
        String targetModelId = (modelId == null || modelId.isEmpty()) ? defaultModelId : modelId;
        ChatModel chatModel = multiModelFactory.getChatModel(targetModelId);

        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .build();

        String userPrompt = "请根据下面的 AST 抽象语法树数据，分析该项目的架构并生成架构描述：\n\n" + astData;

        String result = chatClient.prompt()
                .user(userPrompt)
                .call()
                .content();

        log.info("架构分析完成，结果长度: {} 字符", result != null ? result.length() : 0);
        return result;
    }
}