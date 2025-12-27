package com.wfh.drawio.ws.handler;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * @Title: YjsHandler
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ws.handler
 * @Date 2025/12/27 14:19
 * @description:
 */
@Slf4j
@Component
public class YjsHandler extends BinaryWebSocketHandler {

    /**
     * 房间映射
     */
    private final Map<String, Set<WebSocketSession>> roomSession = new ConcurrentHashMap<>();

    /**
     * 房间历史数据
     */
    private final Map<String, List<byte[]>> roomHistory = new ConcurrentHashMap<>();

    /**
     * 连接建立之后
     * @param session
     * @throws Exception
     */
    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) throws Exception {
        String roomName = getRoomName(session);
        String userId = (String) session.getAttributes().get("userId");
        // todo 校验用户权限
        // 加入房间管理
        roomSession.computeIfAbsent(roomName, k-> new CopyOnWriteArraySet<>()).add(session);
        log.info("用户加入房间: {}, Session: {}", roomName, session.getId());
        // 新用户连接的时候，发送该房间的历史数据
        List<byte[]> history = roomHistory.get(roomName);
        if (history != null && !history.isEmpty()){
            for (byte[] bytes : history) {
                session.sendMessage(new BinaryMessage(bytes));
            }
        }
    }

    /**
     * 处理前端发送的二进制消息
     * @param session
     * @param message
     * @throws Exception
     */
    @Override
    protected void handleBinaryMessage(@NotNull WebSocketSession session, BinaryMessage message) throws Exception {
        String roomName = getRoomName(session);
        byte[] payload = message.getPayload().array();

        // 持久化，保存更新数据
        roomHistory.computeIfAbsent(roomName, k->new CopyOnWriteArrayList<>()).add(payload);
        // 广播，转发给同房间的其他的用户
        Set<WebSocketSession> webSocketSessions = roomSession.get(roomName);
        if (webSocketSessions != null){
            for (WebSocketSession webSocketSession : webSocketSessions) {
                // 排除自己，只发给别人
                if (webSocketSession.isOpen() && !webSocketSession.getId().equals(session.getId())){
                    // 发送消息
                    webSocketSession.sendMessage(new BinaryMessage(payload));
                }
            }
        }
    }

    /**
     * 连接关闭之后
     * @param session
     * @param status
     */
    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) {
        String roomName = getRoomName(session);
        Set<WebSocketSession> sessions = roomSession.get(roomName);
        if (sessions != null) {
            sessions.remove(session);
            // 如果房间空了，可以选择清理内存中的 history (如果已持久化到数据库)
            if (sessions.isEmpty()) {
                System.out.println("房间 " + roomName + " 已空闲");
            }
        }
    }

    /**
     * 从URL中取出房间名
     * @param session
     * @return
     */
    private String getRoomName(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return "default";
        }
        String path = uri.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
