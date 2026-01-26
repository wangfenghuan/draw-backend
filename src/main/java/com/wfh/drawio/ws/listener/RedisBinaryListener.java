package com.wfh.drawio.ws.listener;

import com.wfh.drawio.ws.handler.YjsHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * @author fenghuanwang
 */
@Slf4j
@Component
public class RedisBinaryListener implements MessageListener {

    @Resource
    @Lazy
    private YjsHandler yjsHandler;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // 1. 从 Channel Name 解析 RoomID
            // Channel: "drawio:room:123"
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            String roomId = channel.substring(channel.lastIndexOf(':') + 1);

            // 2. 获取消息体（已经是完整格式：[idLen][senderId][payload]）
            byte[] body = message.getBody();
            if (body.length <= 1) return;

            ByteBuffer buffer = ByteBuffer.wrap(body);

            // 3. 解析 SenderID（用于排除发送者）
            int idLen = Byte.toUnsignedInt(buffer.get());
            byte[] idBytes = new byte[idLen];
            buffer.get(idBytes);
            String senderId = new String(idBytes, StandardCharsets.UTF_8);

            // 4. 转发完整的消息体给本地用户（包含前缀，不再重新构造）
            yjsHandler.dispatchToLocalUsers(roomId, senderId, body);

            log.debug("📥 从 Redis 收到消息并转发给房间 {}，发送者: {}", roomId, senderId);

        } catch (Exception e) {
            log.error("❌ Redis 广播解包失败", e);
        }
    }
}