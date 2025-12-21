package com.wfh.drawio.ai.client;

import com.wfh.drawio.ai.advisor.MyLoggerAdvisor;
import com.wfh.drawio.ai.utils.PromptUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @Title: DrawClient
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ai.client
 * @Date 2025/12/20 19:54
 * @description: 画图ai客户端
 */
@Component
public class DrawClient {

    private final ChatClient chatClient;

    @Value("${spring.ai.openai.chat.options.model}")
    private String model;

    public DrawClient(ChatModel openaiChatModel){
        chatClient = ChatClient.builder(openaiChatModel)
                .defaultSystem(PromptUtil.getSystemPrompt(model, true))
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
    }

    /**
     * 基础对话
     * @param message
     * @return
     */
    public String doChat(String message){
        ChatResponse chatResponse = this.chatClient
                .prompt()
                .user(message)
                .call()
                .chatResponse();
        String text = chatResponse.getResult().getOutput().getText();
        return text;
    }

}
