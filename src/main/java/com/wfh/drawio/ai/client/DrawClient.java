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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

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
    public Flux<String> doChatStream(String message, String diagramId, String modelId){
        // 创建旁路管道，用于接收工具层发出的日志和结果
        Sinks.Many<StreamEvent> sideChannelSink = Sinks.many().unicast().onBackpressureBuffer();
        Flux<String> toolLogFlux = sideChannelSink.asFlux()
                .map(JSONUtil::toJsonStr);
        ChatClient chatClient = createChatClient(modelId);
        Flux<String> aiResFlux = chatClient.prompt()
                .user(message)
                // 不再手动设置工具，让Spring AI自动发现
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, diagramId))
                .stream()
                .content()
                .map(text -> JSONUtil.toJsonStr(StreamEvent.builder()
                        .type("text")
                        .content(text)
                        .build()))
                .contextCapture()
                // 当ai流结束的时候关闭旁路管道
                .doOnTerminate(sideChannelSink::tryEmitComplete);


        // 合并流+绑定上下文
        return  Flux.merge(toolLogFlux, aiResFlux)
                .doOnSubscribe(subscription -> {
                    // 开始请求的时候把sink和diagramId放入ThreadLocal和ScopedValue
                    DiagramContextUtil.bindSink(sideChannelSink);
                    DiagramContextUtil.bindConversationId(diagramId);
                })
                .doFinally(signalType -> {
                    DiagramContextUtil.clear();
                });
    }
}
