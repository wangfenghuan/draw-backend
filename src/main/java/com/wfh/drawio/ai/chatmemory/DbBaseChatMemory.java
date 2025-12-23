package com.wfh.drawio.ai.chatmemory;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wfh.drawio.model.entity.Conversion;
import com.wfh.drawio.model.entity.Diagram;
import com.wfh.drawio.service.ConversionService;
import com.wfh.drawio.service.DiagramService;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
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
    
    
    @Resource
    private ConversionService conversionService;

    @Resource
    private DiagramService diagramService;


    /**
     * 加入消息
     * @param conversationId
     * @param messages
     */
    @Override
    public void add(@NotNull String conversationId, @NotNull List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        saveConversation(conversationId, messages);
    }

    /**
     * 获取消息
     * @param conversationId
     * @return
     */
    @Override
    public List<Message> get(String conversationId) {
        return get(conversationId, 20);
    }

    /**
     * 获取消息
     * @param conversationId
     * @param lastN
     * @return
     */
    public List<Message> get(String conversationId, int lastN) {
        List<Message> messageList = getOrCreateConversation(conversationId);
        int fromIndex = Math.max(0, messageList.size() - lastN);
        return new ArrayList<>(messageList.subList(fromIndex, messageList.size()));
    }

    /**
     * 保存消息
     * @param diagramId
     * @param messages
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveConversation(String diagramId, List<Message> messages) {
        Long id = Long.valueOf(diagramId);

        // 只查询 userId 字段，避免加载整个 Diagram 对象
        Diagram diagram = diagramService.lambdaQuery()
                .select(Diagram::getUserId)
                .eq(Diagram::getId, id)
                .one();

        if (diagram == null) {
            log.error("对话不存在: {}", diagramId);
            return;
        }
        Long userId = diagram.getUserId();

        // 准备一个列表用于批量插入
        List<Conversion> batchList = new ArrayList<>(messages.size());

        for (Message message : messages) {
            Conversion conversion = new Conversion();
            conversion.setDiagramId(id);
            conversion.setUserId(userId);
            boolean needSave = false; // 标记是否生成了有效内容

            // 1. 处理 AI 消息
            if (message instanceof AssistantMessage am) {
                StringBuilder stringBuilder = new StringBuilder();

                // 获取普通文本
                String text = am.getText();
                if (StringUtils.hasText(text)) {
                    stringBuilder.append(text);
                }

                // 获取工具调用
                List<AssistantMessage.ToolCall> toolCalls = am.getToolCalls();
                if (toolCalls != null && !toolCalls.isEmpty()) {
                    if (stringBuilder.length() > 0) {
                        stringBuilder.append("\n\n");
                    }
                    for (AssistantMessage.ToolCall toolCall : toolCalls) {
                        String arguments = toolCall.arguments();
                        if (StringUtils.hasText(arguments)) {
                            stringBuilder.append(arguments).append("\n");
                        }
                    }
                }
                String finalContent = stringBuilder.toString();
                if (StringUtils.hasText(finalContent)) {
                    conversion.setMessageType("ai");
                    conversion.setMessage(finalContent);
                    needSave = true;
                }
            }
            // 2. 处理用户消息
            else if (message instanceof UserMessage) {
                conversion.setMessageType("user");
                conversion.setMessage(message.getText());
                needSave = true;
            }
            // 其他类型跳过
            else {
                log.debug("跳过不需要存储的消息类型: {}", message.getMessageType());
            }

            //加入列表
            if (needSave) {
                batchList.add(conversion);
            }
        }

        // 循环结束后，一次性批量插入
        if (!batchList.isEmpty()) {
            conversionService.saveBatch(batchList);
        }
    }

    /**
     * 获取或创建消息
     * @param conversationId
     * @return
     */
    public List<Message> getOrCreateConversation(String conversationId) {
        List<Conversion> conversions = conversionService.getBaseMapper().selectList(
                new QueryWrapper<>(Conversion.class)
                        .eq("diagramId", conversationId)
                        .orderByAsc("id")
        );
        List<Message> messageList = new ArrayList<>();
        if (conversions != null && !conversions.isEmpty()) {
            for (Conversion conversion : conversions) {
                String type = conversion.getMessageType();
                String content = conversion.getMessage();
                if (!StringUtils.hasText(content)) continue;

                if ("user".equals(type)) {
                    messageList.add(new UserMessage(content));
                } else if ("ai".equals(type)) {
                    messageList.add(new AssistantMessage(content));
                }
            }
        }
        return messageList;
    }

    /**
     * 清除消息
     * @param conversationId
     */
    @Override
    public void clear(String conversationId) {
        try {
            Long diagramId = Long.valueOf(conversationId);
            conversionService.getBaseMapper()
                    .delete(new QueryWrapper<Conversion>().eq("diagramId", diagramId));
        } catch (NumberFormatException e) {
            log.warn("无法清除会话，ID 非法: {}", conversationId);
        }
    }
}
