package com.wfh.drawio.ai.client;

import cn.hutool.json.JSONUtil;
import com.wfh.drawio.ai.advisor.MyLoggerAdvisor;
import com.wfh.drawio.ai.chatmemory.DbBaseChatMemory;
import com.wfh.drawio.ai.config.MultiModelFactory;
import com.wfh.drawio.ai.model.StreamEvent;
import com.wfh.drawio.ai.tools.AppendDiagramTool;
import com.wfh.drawio.ai.tools.CreateDiagramTool;
import com.wfh.drawio.ai.tools.EditDiagramTool;
import com.wfh.drawio.ai.utils.DiagramContextUtil;
import com.wfh.drawio.ai.utils.PromptUtil;
import com.wfh.drawio.model.dto.diagram.CustomChatRequest;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Objects;

/**
 * @Title: DrawClient
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ai.client
 * @Date 2025/12/20 19:54
 * @description: 画图ai客户端
 */
@Component
public class DrawClient {

    private final MultiModelFactory multiModelFactory;

    private final DbBaseChatMemory dbBaseChatMemory;

    private final CreateDiagramTool createDiagramTool;
    private final EditDiagramTool editDiagramTool;
    private final AppendDiagramTool appendDiagramTool;

    @Value("${spring.ai.openai.chat.options.model}")
    private String defaultModelId;

    public DrawClient(MultiModelFactory multiModelFactory, DbBaseChatMemory dbBaseChatMemory,
                      CreateDiagramTool createDiagramTool,
                      EditDiagramTool editDiagramTool,
                      AppendDiagramTool appendDiagramTool) {
        this.multiModelFactory = multiModelFactory;
        this.dbBaseChatMemory = dbBaseChatMemory;
        this.createDiagramTool = createDiagramTool;
        this.editDiagramTool = editDiagramTool;
        this.appendDiagramTool = appendDiagramTool;
    }

    /**
     * 动态构建client
     * @param modelId
     * @return
     */
    private ChatClient createChatClient(String modelId){
        String targetModelId = (modelId == null || modelId.isEmpty()) ? defaultModelId : modelId;
        ChatModel chatModel = multiModelFactory.getChatModel(targetModelId);
        return ChatClient.builder(chatModel)
                .defaultTools(createDiagramTool)
                .defaultTools(editDiagramTool)
                .defaultTools(appendDiagramTool)
                .defaultSystem(PromptUtil.getSystemPrompt(targetModelId, true))
                .defaultAdvisors(new MyLoggerAdvisor())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(dbBaseChatMemory).build())
                .build();
    }

    /**
     * 创建自定义的client
     *
     * @param modelId
     * @param apiKey
     * @param baseUrl
     * @return
     */
    private ChatClient createCustomChatClient(String modelId, String apiKey, String baseUrl) {
        ChatModel customModel = multiModelFactory.getCustomModel(modelId, apiKey, baseUrl);
        return ChatClient.builder(customModel)
                .defaultTools(createDiagramTool)
                .defaultTools(editDiagramTool)
                .defaultTools(appendDiagramTool)
                .defaultSystem(PromptUtil.getSystemPrompt(modelId, true))
                .defaultAdvisors(new MyLoggerAdvisor())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(dbBaseChatMemory).build())
                .build();
    }

    /**
     * 基础对话
     * @param message
     * @return
     */
    public String doChat(String message, String diagramId, String modelId){
        ChatClient chatClient = createChatClient(modelId);
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                // 不再手动设置工具，让Spring AI自动发现
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, diagramId))
                .call()
                .chatResponse();
        String text = chatResponse.getResult().getOutput().getText();
        return text;
    }


    /**
     * 调用工具流式对话
     * @param message
     * @param diagramId
     * @return
     */
    public Flux<String> doChatStream(String message, String diagramId, String modelId) {
        ChatClient chatClient = createChatClient(modelId);
        return getStringFlux(message, diagramId, chatClient);
    }

    /**
     * 自定义模型流式响应
     *
     * @param request
     * @return
     */
    public Flux<String> doCustomChatStream(CustomChatRequest request) {
        String message = request.getMessage();
        String diagramId = request.getDiagramId();
        String modelId = request.getModelId();
        String baseUrl = request.getBaseUrl();
        String apiKey = request.getApiKey();
        ChatClient customChatClient = createCustomChatClient(modelId, apiKey, baseUrl);
        // 1. 创建旁路管道
        return getStringFlux(message, diagramId, customChatClient);
    }

    /**
     * 获取流式响应
     *
     * @param message
     * @param diagramId
     * @param chatClient
     * @return
     */
    @NotNull
    private Flux<String> getStringFlux(String message, String diagramId, ChatClient chatClient) {
        // 1. 创建旁路管道
        Sinks.Many<StreamEvent> sideChannelSink = Sinks.many().unicast().onBackpressureBuffer();
        // 2. 将管道转换为流，并处理 JSON 序列化
        Flux<String> toolLogFlux = sideChannelSink.asFlux()
                .map(JSONUtil::toJsonStr);
        // 3. AI 主回复流
        Flux<String> aiResFlux = chatClient.prompt()
                .user(message)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, diagramId))
                .stream()
                .content()
                .filter(Objects::nonNull)
                .map(text -> JSONUtil.toJsonStr(StreamEvent.builder()
                        .type("text")
                        .content(text)
                        .build()))
                // 当 AI 流结束时，关闭旁路管道。这是 Flux.merge 能结束的关键。
                .doOnTerminate(sideChannelSink::tryEmitComplete);
        // 4. 合并流 + 注入上下文
        return Flux.merge(toolLogFlux, aiResFlux)
                // 使用 contextWrite 显式注入 Sink，确保它存在于 Reactor Context 中
                .contextWrite(ctx -> ctx
                        .put(DiagramContextUtil.KEY_SINK, sideChannelSink)
                        .put(DiagramContextUtil.KEY_CONVERSATION_ID, diagramId)
                )
                // 这里的 doFinally 只负责清理 ThreadLocal 防止污染，不负责传递
                .doFinally(signalType -> DiagramContextUtil.clear());
    }
}
