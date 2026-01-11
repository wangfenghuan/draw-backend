package com.wfh.drawio.ws.handler;

import com.wfh.drawio.mapper.DiagramRoomMapper;
import com.wfh.drawio.model.entity.DiagramRoom;
import com.wfh.drawio.service.DiagramRoomService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.Collection;
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

    private static final byte OP_SYNC = 0x00;        // 刚进房间拉取数据
    private static final byte OP_POINTER = 0x01;     // 鼠标移动 (Awareness)
    private static final byte OP_ELEMENTS = 0x02;    // 画图/修改/删除 (关键!)

    @Resource
    private DiagramRoomMapper roomMapper;

    @Resource
    private DiagramRoomService diagramRoomService;


    /**
     * 连接建立：发送最新的加密数据给客户端，并广播用户数
     * @param session
     * @throws Exception
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 权限校验
        if (!hasPermission(session.getPrincipal(), "diagram:view")){
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        String roomId = getRoomId(session);
        roomSessions.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(session);

        log.info("✅ 用户加入协作房间: {}, 当前房间人数: {}", roomId, roomSessions.get(roomId).size());

        // A. 查库：获取该房间最新的加密快照，发送给新加入的用户
        DiagramRoom room = roomMapper.selectById(roomId);
        if (room != null && room.getEncryptedData() != null) {
            byte[] encryptedData = room.getEncryptedData();
            // 构造同步标志
            ByteBuffer initPayload = ByteBuffer.allocate(1 + encryptedData.length);
            initPayload.put(OP_SYNC);
            initPayload.put(encryptedData);
            initPayload.flip();
            try {
                session.sendMessage(new BinaryMessage(initPayload));
                log.info("📤 发送房间 {} 的加密快照，数据大小: {} bytes", roomId, encryptedData.length);
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

        ByteBuffer buffer = message.getPayload();
        if (buffer.remaining() < 1){
            return;
        }
        // 读取首字节
        byte msgType = buffer.get(0);
        // 获取用户权限
        Principal principal = session.getPrincipal();
        boolean canView = hasPermission(principal, "diagram:view");
        boolean canEdit = hasPermission(principal, "diagram:edit");
        // 无查看权限直接断开
        if (!canView){
            session.close();
            return;
        }
        String roomId = getRoomId(session);
        switch (msgType){
            case OP_POINTER -> broadcast(roomId, message, session.getId());
            case OP_ELEMENTS -> {
                // 检查编辑权限
                if (canEdit){
                    broadcast(roomId, message , session.getId());
                    // 异步存库
                    byte[] data = new byte[buffer.remaining() - 1];
                    // 移动指针跳过第0位，读取剩余数据
                    buffer.position(1);
                    buffer.get(data);
                    dbExecutor.submit(() -> saveSnapshot(roomId, data));
                }else {
                    // 无权操作：忽略或发送错误提示
                    log.warn("⛔ 拦截无权写操作: user={}", principal.getName());
                    // sendError(session, "您处于访客模式，无法编辑");
                }
            }
            default -> {}
        }
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
            diagramRoomService.saveOrUpdate(room);
        } catch (Exception e) {
            log.error("❌ 保存房间 {} 数据失败: {}", roomId, e.getMessage());
        }
    }

    /**
     * 广播二进制数据给房间内其他用户
     * @param roomId
     * @param message
     * @param senderId
     */
    private void broadcast(String roomId, BinaryMessage message, String senderId) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions != null) {
            ByteBuffer payload = message.getPayload();
            ByteBuffer duplicate = payload.duplicate();
            for (WebSocketSession session : sessions) {
                if (session.isOpen() && !session.getId().equals(senderId)){
                    try{
                        session.sendMessage(new BinaryMessage(duplicate.duplicate()));
                    }catch (Exception e){
                        log.error("广播失败");
                    }
                }
            }
            log.debug("📡 房间 {} 广播", roomId);
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


    /**
     * 校验权限
     * @param principal
     * @param targetPerm
     * @return
     */
    private boolean hasPermission(Principal principal, String targetPerm){
        // 未登录直接拒绝
        if (principal == null){
            return false;
        }
        if (principal instanceof Authentication){
            Authentication auth = (Authentication) principal;
            // 获取所有权限
            Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
            if (authorities == null || authorities.isEmpty()){
                return false;
            }
            // 遍历权限
            for (GrantedAuthority authority : authorities) {
                String myPerm = authority.getAuthority();
                if ("admin".equals(myPerm)){
                    // 超级管理员直接放行
                    return true;
                }
                if (myPerm.equals(targetPerm)){
                    return true;
                }
            }
        }

        return false;
    }
}