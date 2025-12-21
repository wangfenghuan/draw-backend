package com.wfh.drawio.ai.chatmemory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Title: DbBaseChatMemory
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ai.chatmemory
 * @Date 2025/12/20 20:17
 * @description:
 */
@Component
@Slf4j
public class DbBaseChatMemory implements ChatMemory {
    @Override
    public void add(String conversationId, Message message) {
        ChatMemory.super.add(conversationId, message);
    }

    @Override
    public void add(String conversationId, List<Message> messages) {

    }

    @Override
    public List<Message> get(String conversationId) {
        return List.of();
    }

    @Override
    public void clear(String conversationId) {

    }
}
