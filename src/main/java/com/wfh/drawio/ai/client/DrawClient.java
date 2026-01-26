package com.wfh.drawio.ai.client;

import cn.hutool.json.JSONUtil;
import com.wfh.drawio.ai.advisor.MyLoggerAdvisor;
import com.wfh.drawio.ai.chatmemory.DbBaseChatMemory;
import com.wfh.drawio.ai.config.MultiModelFactory;
import com.wfh.drawio.ai.model.StreamEvent;
import com.wfh.drawio.ai.tools.AppendDiagramTool;
import com.wfh.drawio.ai.tools.CreateDiagramTool;
import com.wfh.drawio.ai.tools.EditDiagramTool;
import com.wfh.drawio.ai.utils.PromptUtil;
import com.wfh.drawio.model.dto.diagram.CustomChatRequest;
import com.wfh.drawio.service.DiagramService;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
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

    private final DiagramService diagramService;

    @Value("${spring.ai.openai.chat.options.model}")
    private String defaultModelId;

    public DrawClient(MultiModelFactory multiModelFactory, DbBaseChatMemory dbBaseChatMemory, DiagramService diagramService) {
        this.multiModelFactory = multiModelFactory;
        this.dbBaseChatMemory = dbBaseChatMemory;
        this.diagramService = diagramService;
    }

    /**
     * 动态构建client
     * @param modelId
     * @return
     */
    private ChatClient createChatClient(String modelId, String diagramId, Sinks.Many<StreamEvent> sink){
        Resource cpRes = new ClassPathResource("/doc/xml_guide.md");
        String targetModelId = (modelId == null || modelId.isEmpty()) ? defaultModelId : modelId;
        ChatModel chatModel = multiModelFactory.getChatModel(targetModelId);
        
        // 实例化带有上下文的工具
        CreateDiagramTool createTool = new CreateDiagramTool(diagramService, diagramId, sink);
        EditDiagramTool editTool = new EditDiagramTool(diagramService, diagramId, sink);
        AppendDiagramTool appendTool = new AppendDiagramTool(diagramService, diagramId, sink);

        return ChatClient.builder(chatModel)
                .defaultTools(createTool)
                .defaultTools(editTool)
                .defaultTools(appendTool)
                .defaultSystem(cpRes)
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
    private ChatClient createCustomChatClient(String modelId, String apiKey, String baseUrl, String diagramId, Sinks.Many<StreamEvent> sink) {
        ChatModel customModel = multiModelFactory.getCustomModel(modelId, apiKey, baseUrl);
        
        // 实例化带有上下文的工具
        CreateDiagramTool createTool = new CreateDiagramTool(diagramService, diagramId, sink);
        EditDiagramTool editTool = new EditDiagramTool(diagramService, diagramId, sink);
        AppendDiagramTool appendTool = new AppendDiagramTool(diagramService, diagramId, sink);

        return ChatClient.builder(customModel)
                .defaultTools(createTool)
                .defaultTools(editTool)
                .defaultTools(appendTool)
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
        // 同步调用不需要 Sink，或者传入 null
        ChatClient chatClient = createChatClient(modelId, diagramId, null);
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
        // 1. 创建旁路管道
        Sinks.Many<StreamEvent> sideChannelSink = Sinks.many().unicast().onBackpressureBuffer();
        ChatClient chatClient = createChatClient(modelId, diagramId, sideChannelSink);
        return getStringFlux(message, diagramId, chatClient, sideChannelSink);
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
        
        // 1. 创建旁路管道
        Sinks.Many<StreamEvent> sideChannelSink = Sinks.many().unicast().onBackpressureBuffer();
        ChatClient customChatClient = createCustomChatClient(modelId, apiKey, baseUrl, diagramId, sideChannelSink);
        return getStringFlux(message, diagramId, customChatClient, sideChannelSink);
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
    private Flux<String> getStringFlux(String message, String diagramId, ChatClient chatClient, Sinks.Many<StreamEvent> sideChannelSink) {
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
        // 4. 合并流 (不再需要注入上下文)
        return Flux.merge(toolLogFlux, aiResFlux);
    }
}
