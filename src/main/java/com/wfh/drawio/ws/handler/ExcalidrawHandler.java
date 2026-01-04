package com.wfh.drawio.ws.handler;

import com.wfh.drawio.mapper.DiagramRoomMapper;
import com.wfh.drawio.model.entity.CooperationRoom;
import com.wfh.drawio.model.entity.DiagramRoom;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.web.socket.TextMessage;

/**
 * Excalidraw 风格的 WebSocket 处理器
 *
 * 功能:
 * 1. 接收加密的二进制数据并广播
 * 2. 异步持久化到数据库
 * 3. 实时统计和广播在线人数
 *
 * @author fenghuanwang
 */
@Component
@Slf4j
public class ExcalidrawHandler extends BinaryWebSocketHandler {

    /**
     * 房间管理
     */
    private final Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    /**
     * 异步线程池存库
     */
    private final ExecutorService dbExecutor = Executors.newFixedThreadPool(4);

    @Resource
    private DiagramRoomMapper roomMapper;


    /**
     * 连接建立：发送最新的加密数据给客户端，并广播用户数
     * @param session
     * @throws Exception
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String roomId = getRoomId(session);
        roomSessions.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(session);

        log.info("✅ 用户加入协作房间: {}, 当前房间人数: {}", roomId, roomSessions.get(roomId).size());

        // A. 查库：获取该房间最新的加密快照，发送给新加入的用户
        DiagramRoom room = roomMapper.selectById(roomId);
        if (room != null && room.getEncryptedData() != null) {
            try {
                session.sendMessage(new BinaryMessage(room.getEncryptedData()));
                log.info("📤 发送房间 {} 的加密快照，数据大小: {} bytes", roomId, room.getEncryptedData().length);
            } catch (IOException e) {
                log.error("❌ 发送加密快照失败: {}", e.getMessage());
            }
        } else {
            log.info("ℹ️ 房间 {} 暂无数据", roomId);
        }

        // B. 广播当前在线人数给房间内所有人
        broadcastUserCount(roomId);
    }

    /**
     * 收到消息：广播 + 持久化
     * @param session
     * @param message
     * @throws Exception
     */
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        String roomId = getRoomId(session);

        // 获取二进制负载 (这是前端加密过的)
        byte[] payload = message.getPayload().array();

        log.debug("📨 收到房间 {} 的加密数据，大小: {} bytes", roomId, payload.length);

        // A. 广播: 毫秒级转发给其他人
        broadcast(roomId, payload, session.getId());

        // B. 持久化: 异步存入 MySQL
        dbExecutor.submit(() -> {
            saveSnapshot(roomId, payload);
        });
    }

    /**
     * 连接关闭并广播用户数量
     * @param session
     * @param status
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomId = getRoomId(session);
        Set<WebSocketSession> sessions = roomSessions.get(roomId);

        if (sessions != null) {
            sessions.remove(session);

            // 广播更新后的用户数
            broadcastUserCount(roomId);

            log.info("👋 用户离开协作房间: {}, 当前房间人数: {}", roomId, sessions.size());

            // 如果房间空了，清理房间
            if (sessions.isEmpty()) {
                roomSessions.remove(roomId);
                log.info("🧹 房间 {} 已清空", roomId);
            }
        }
    }

    /**
     * 保存图表快照
     * @param roomId
     * @param data
     */
    private void saveSnapshot(String roomId, byte[] data) {
        try {
            DiagramRoom room = new DiagramRoom();
            room.setId(Long.valueOf(roomId));
            room.setEncryptedData(data);
            // UPSERT: 存在即更新，不存在即插入
            DiagramRoom exist = roomMapper.selectById(roomId);
            if (exist == null) {
                roomMapper.insert(room);
                log.info("💾 房间 {} 数据已插入", roomId);
            } else {
                roomMapper.updateById(room);
                log.info("💾 房间 {} 数据已更新", roomId);
            }
        } catch (Exception e) {
            log.error("❌ 保存房间 {} 数据失败: {}", roomId, e.getMessage());
        }
    }

    /**
     * 广播二进制数据给房间内其他用户
     * @param roomId
     * @param payload
     * @param senderId
     */
    private void broadcast(String roomId, byte[] payload, String senderId) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions != null) {
            int successCount = 0;
            for (WebSocketSession s : sessions) {
                if (s.isOpen() && !s.getId().equals(senderId)) {
                    try {
                        s.sendMessage(new BinaryMessage(payload));
                        successCount++;
                    } catch (IOException e) {
                        log.error("❌ 广播消息失败: {}", e.getMessage());
                    }
                }
            }
            log.debug("📡 房间 {} 广播给 {} 人", roomId, successCount);
        }
    }

    /**
     * 广播用户数变化
     * @param roomId
     */
    private void broadcastUserCount(String roomId) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("⏭️ 房间 {} 不存在或为空，跳过用户数广播", roomId);
            return;
        }

        int userCount = sessions.size();
        String jsonMessage = String.format("{\"type\":\"user_count\",\"count\":%d}", userCount);

        log.info("📊 广播用户数: 房间={}, 人数={}", roomId, userCount);

        int successCount = 0;
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    // 使用 TextMessage 发送 JSON
                    session.sendMessage(new TextMessage(jsonMessage));
                    successCount++;
                } catch (IOException e) {
                    log.error("❌ 发送用户数失败: {}", e.getMessage());
                }
            }
        }

        log.info("📤 用户数消息已发送给 {} 人", successCount);
    }

    /**
     * 从 URL 提取房间 ID
     * @param session
     * @return
     */
    private String getRoomId(WebSocketSession session) {
        String path = Objects.requireNonNull(session.getUri()).getPath();
        // /api/excalidraw/{roomId} -> 提取最后一部分
        return path.substring(path.lastIndexOf('/') + 1);
    }
}