package com.wfh.drawio.ws.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @Title: CollaborationService
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ws.service
 * @Date 2026/1/22 19:43
 * @description:
 */
@Service
@Slf4j
public class CollaborationService {

    @Resource
    private RedisTemplate<String, byte[]> bytesRedisTemplate;

    private static final String CHANNEL_PREFIX = "drawio:room:";
    private static final String LIST_PREFIX = "drawio:updates:";

    // Yjs 操作码
    private static final byte OP_UPDATE = 0x02;

    public void handleIncomingMessage(String roomId, String senderId, byte[] rawPayload){
        if (rawPayload == null | rawPayload.length == 0){
            return;
        }
        // 持久化判断
        if (rawPayload[0] == OP_UPDATE){
            bytesRedisTemplate.opsForList().rightPush(LIST_PREFIX + roomId, rawPayload);
            // 延长过期时间
            bytesRedisTemplate.expire(LIST_PREFIX + roomId, 24, TimeUnit.HOURS);
        }
        // 构造广播包
        byte[] idBytes = senderId.getBytes(StandardCharsets.UTF_8);
        if (idBytes.length > 255){
            log.warn("发送者id太长");
        }
        byte idLen = (byte) idBytes.length;
        // 申请内存
        ByteBuffer buffer = ByteBuffer.allocate(1 + idLen + rawPayload.length);
        buffer.put(idLen);
        buffer.put(idBytes);
        buffer.put(rawPayload);
        // 发送
        bytesRedisTemplate.convertAndSend(CHANNEL_PREFIX + roomId, buffer.array());
    }

    /**
     * 获取 Redis 中的增量数据 (用于新用户加入)
     */
    public List<byte[]> getBufferedUpdates(String roomId) {
        return bytesRedisTemplate.opsForList().range(LIST_PREFIX + roomId, 0, -1);
    }

    /**
     * 清理 Redis 缓存 (当持久化到 MySQL/MinIO 后调用)
     */
    public void trimBufferedUpdates(String roomId, long count) {
        bytesRedisTemplate.opsForList().trim(LIST_PREFIX + roomId, count, -1);
    }

}
