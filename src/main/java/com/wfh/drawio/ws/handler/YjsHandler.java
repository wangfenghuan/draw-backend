package com.wfh.drawio.ws.handler;

import com.wfh.drawio.mapper.DiagramRoomMapper;
import com.wfh.drawio.mapper.RoomSnapshotsMapper;
import com.wfh.drawio.mapper.RoomUpdatesMapper;
import com.wfh.drawio.mapper.SpaceUserMapper;
import com.wfh.drawio.model.entity.DiagramRoom;
import com.wfh.drawio.model.entity.RoomSnapshots;
import com.wfh.drawio.model.entity.RoomUpdates;
import com.wfh.drawio.model.entity.Space;
import com.wfh.drawio.model.entity.SpaceUser;
import com.wfh.drawio.model.enums.AuthorityEnums;
import com.wfh.drawio.model.enums.SpaceTypeEnum;
import com.wfh.drawio.service.SpaceService;
import com.wfh.drawio.ws.service.CollaborationService;
import com.wfh.drawio.ws.service.RoomUpdateBatchService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

    @Resource
    private CollaborationService collaborationService;

    /**
     * 房间映射
     */
    private final Map<String, Set<WebSocketSession>> roomSession = new ConcurrentHashMap<>();

    /**
     * Yjs 操作码定义
     */
    private static final byte OP_POINTER = 0x01;
    private static final byte OP_UPDATE = 0x02;

    @Resource
    private RoomSnapshotsMapper roomSnapshotsMapper;


    @Resource
    private DiagramRoomMapper diagramRoomMapper;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserMapper spaceUserMapper;


    @Resource
    private S3Presigner s3Presigner;

    @Value("${rustfs.client.bucket-name}")
    private String bucketName;

    /**
     * 连接建立之后
     * @param session
     * @throws Exception
     */
    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) throws Exception {
        // 基础权限校验
        if (!hasPermission(session.getPrincipal(), AuthorityEnums.ROOM_DIAGRAM_VIEW.getValue())) {
            session.close(CloseStatus.POLICY_VIOLATION);
            log.warn("❌ 用户无 {} 权限", AuthorityEnums.ROOM_DIAGRAM_VIEW.getValue());
            return;
        }

        // 获取房间ID
        String roomName = getRoomName(session);
        Long roomId;
        try {
            roomId = Long.valueOf(roomName);
        } catch (NumberFormatException e) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            log.warn("❌ 房间ID格式错误: {}", roomName);
            return;
        }

        // 查询房间信息
        DiagramRoom room = diagramRoomMapper.selectById(roomId);
        if (room == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("房间不存在"));
            log.warn("❌ 房间不存在: {}", roomId);
            return;
        }

        // 校验房间权限
        Principal principal = session.getPrincipal();
        if (principal == null || !(principal instanceof Authentication)) {
            session.close(CloseStatus.POLICY_VIOLATION);
            log.warn("❌ 未登录用户尝试连接协作房间");
            return;
        }

        Authentication auth = (Authentication) principal;
        Object principalObj = auth.getPrincipal();
        if (!(principalObj instanceof com.wfh.drawio.model.entity.User)) {
            session.close(CloseStatus.POLICY_VIOLATION);
            log.warn("❌ 无法获取用户信息");
            return;
        }

        com.wfh.drawio.model.entity.User loginUser = (com.wfh.drawio.model.entity.User) principalObj;

        // 校验空间权限
        Long spaceId = room.getSpaceId();
        if (spaceId == null) {
            // 公共房间：仅房主或管理员可访问
            if (!room.getOwnerId().equals(loginUser.getId()) && !isAdmin(auth)) {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("无权限访问此公共房间"));
                log.warn("❌ 用户 {} 无权限访问公共房间 {}", loginUser.getId(), roomId);
                return;
            }
        } else {
            // 查询空间信息
            Space space = spaceService.getById(spaceId);
            if (space == null) {
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("空间不存在"));
                log.warn("❌ 空间不存在: {}", spaceId);
                return;
            }

            if (SpaceTypeEnum.PRIVATE.getValue() == space.getSpaceType()) {
                // 私有空间：仅空间创建人
                if (!space.getUserId().equals(loginUser.getId()) && !isAdmin(auth)) {
                    session.close(CloseStatus.POLICY_VIOLATION.withReason("无权限访问私有空间"));
                    log.warn("❌ 用户 {} 无权限访问私有空间 {}", loginUser.getId(), spaceId);
                    return;
                }
            } else if (SpaceTypeEnum.TEAM.getValue() == space.getSpaceType()) {
                // 团队空间：查询 SpaceUser 表校验角色
                SpaceUser spaceUser = spaceUserMapper.selectOne(
                    new LambdaQueryWrapper<SpaceUser>()
                        .eq(SpaceUser::getSpaceId, spaceId)
                        .eq(SpaceUser::getUserId, loginUser.getId())
                );
                if (spaceUser == null && !isAdmin(auth)) {
                    session.close(CloseStatus.POLICY_VIOLATION.withReason("不是团队空间成员"));
                    log.warn("❌ 用户 {} 不是团队空间 {} 的成员", loginUser.getId(), spaceId);
                    return;
                }
            }
        }

        // 权限校验通过，加入房间管理
        roomSession.computeIfAbsent(roomName, k -> new CopyOnWriteArraySet<>()).add(session);

        log.info("✅ 用户加入协作房间: {}, 当前房间人数: {}", roomName, roomSession.get(roomName).size());

        // --- 阶段一：加载快照 (Base) ---
        RoomSnapshots snapshot = roomSnapshotsMapper.selectLatestByRoom( String.valueOf(roomId) );
        if (snapshot != null && snapshot.getObjectKey() != null) {
            try {
                // 1. 构造获取对象的请求
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(snapshot.getObjectKey())
                        .build();

                // 2. 构造预签名请求 (有效期 60 分钟)
                GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(60))
                        .getObjectRequest(getObjectRequest)
                        .build();

                // 3. 生成 URL
                PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
                String url = presignedRequest.url().toString();

                // 4. 发送给前端
                session.sendMessage(new TextMessage("{\"type\":\"snapshot\",\"url\":\"" + url + "\"}"));
                log.debug("📥 发送快照 URL 给房间 {}", roomId);

            } catch (Exception e) {
                log.error("生成 S3 预签名 URL 失败，roomId: {}, objectKey: {}", roomId, snapshot.getObjectKey(), e);
                // 这里可以选择不中断流程，只是让前端加载不到底图，或者报错断开
            }
        } else {
            log.info("ℹ️ 房间 {} 暂无快照记录，从零开始加载", roomId);
        }

        // --- 阶段二：加载增量 (Delta) ---
        // 从 Redis List 读取自上次快照以来的所有 Updates
        List<byte[]> updates = collaborationService.getBufferedUpdates(String.valueOf(roomId));
        for (byte[] update : updates) {
            // 透传二进制
            session.sendMessage(new BinaryMessage(update));
        }
        // 广播当前在线人数
        broadcastUserCount(roomName);
    }

    /**
     * 处理前端发送的二进制消息
     * @param session
     * @param message
     * @throws Exception
     */
    @Override
    protected void handleBinaryMessage(@NotNull WebSocketSession session, BinaryMessage message) throws Exception {
        byte[] payload = message.getPayload().array();
        if (payload.length < 1) {
            log.warn("⚠️ 收到空消息");
            return;
        }

        // 读取第一个字节作为 OpCode
        byte opCode = payload[0];
        String roomName = getRoomName(session);

        // 获取用户权限
        Principal principal = session.getPrincipal();
        boolean canView = hasPermission(principal, AuthorityEnums.ROOM_DIAGRAM_VIEW.getValue());
        boolean canEdit = hasPermission(principal, AuthorityEnums.ROOM_DIAGRAM_EDIT.getValue());

        // 无查看权限直接断开
        if (!canView) {
            session.close();
            return;
        }

        log.info("收到消息，房间: {}, OpCode: 0x{}, 长度: {}, 来自: {}",
                roomName, String.format("%02X", opCode), payload.length, session.getId());

        switch (opCode) {
            case OP_POINTER -> {
                // 鼠标移动消息，直接广播不存储
                broadcastBinaryToOthers(roomName, payload, session.getId());
            }
            case OP_UPDATE -> {
                // Yjs 更新消息，需要存储并广播
                if (canEdit) {
                    collaborationService.handleIncomingMessage(roomName, session.getId(), message.getPayload().array());
                } else {
                    log.warn("⛔ 拦截无权编辑操作: user={}", principal != null ? principal.getName() : "anonymous");
                }
            }
            default -> {
                log.warn("⚠️ 未知 OpCode: 0x{}", String.format("%02X", opCode));
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
            // 广播更新后的用户数
            broadcastUserCount(roomName);
            log.info("👋 用户离开协作房间: {}, 当前房间人数: {}", roomName, sessions.size());

            // 如果房间空了，可以选择清理内存中的 history (如果已持久化到数据库)
            if (sessions.isEmpty()) {
                roomSession.remove(roomName);
                log.info("🧹 房间 {} 已清空", roomName);
            }
        }
    }

    /**
     * 处理文本消息（用于用户数统计等 JSON 消息）
     * @param session
     * @param message
     * @throws Exception
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 转发 JSON 消息（如用户数统计）
        String roomName = getRoomName(session);
        broadcastTextToOthers(roomName, message.getPayload(), session.getId());
    }

    /**
     * 广播二进制消息给房间内其他用户
     * 使用与 Redis Pub/Sub 相同的消息格式：[idLen: 1 byte][senderId: N bytes][payload]
     *
     * @param roomName 房间名称
     * @param payload 原始消息载荷（如 [0x02][Yjs Update]）
     * @param senderId 发送者会话ID
     */
    private void broadcastBinaryToOthers(String roomName, byte[] payload, String senderId) {
        Set<WebSocketSession> sessions = roomSession.get(roomName);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        try {
            // 构造与 Redis Pub/Sub 相同的消息格式
            byte[] idBytes = senderId.getBytes(StandardCharsets.UTF_8);
            if (idBytes.length > 255) {
                log.warn("⚠️ 发送者 ID 过长: {}, 长度: {}", senderId, idBytes.length);
                return;
            }

            byte idLen = (byte) idBytes.length;
            ByteBuffer buffer = ByteBuffer.allocate(1 + idLen + payload.length);
            buffer.put(idLen);           // 第1字节：senderId 长度
            buffer.put(idBytes);         // 第2-N字节：senderId
            buffer.put(payload);         // 剩余字节：原始消息

            byte[] formattedPayload = buffer.array();

            // 广播给房间内其他用户
            for (WebSocketSession webSocketSession : sessions) {
                if (webSocketSession.isOpen() && !webSocketSession.getId().equals(senderId)) {
                    try {
                        webSocketSession.sendMessage(new BinaryMessage(formattedPayload));
                    } catch (IOException e) {
                        log.error("❌ 广播消息失败: {}", e.getMessage());
                    }
                }
            }

            log.debug("📡 房间 {} 本地广播完成，接收者数: {}", roomName, sessions.size() - 1);

        } catch (Exception e) {
            log.error("❌ 构造广播消息失败: room={}, sender={}", roomName, senderId, e);
        }
    }

    /**
     * 广播文本消息给房间内其他用户
     * @param roomName
     * @param payload
     * @param senderId
     */
    private void broadcastTextToOthers(String roomName, String payload, String senderId) {
        Set<WebSocketSession> sessions = roomSession.get(roomName);
        if (sessions != null) {
            for (WebSocketSession webSocketSession : sessions) {
                if (webSocketSession.isOpen() && !webSocketSession.getId().equals(senderId)) {
                    try {
                        webSocketSession.sendMessage(new TextMessage(payload));
                    } catch (IOException e) {
                        log.error("❌ 发送文本消息失败: {}", e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * 广播用户数变化
     * @param roomName
     */
    private void broadcastUserCount(String roomName) {
        Set<WebSocketSession> sessions = roomSession.get(roomName);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("⏭️ 房间 {} 不存在或为空，跳过用户数广播", roomName);
            return;
        }
        int userCount = sessions.size();
        String jsonMessage = String.format("{\"type\":\"user_count\",\"count\":%d}", userCount);
        log.info("📊 广播用户数: 房间={}, 人数={}", roomName, userCount);
        int successCount = 0;
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
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
     * 分发消息给房间内的本地用户（用于 Redis Pub/Sub 消息转发）
     * 注意：传入的消息必须已经是完整格式：[idLen][senderId][payload]
     *
     * @param roomId 房间ID
     * @param senderId 发送者ID（用于排除发送者）
     * @param formattedMessage 已格式化的完整消息（包含前缀）
     */
    public void dispatchToLocalUsers(String roomId, String senderId, byte[] formattedMessage) {
        Set<WebSocketSession> sessions = roomSession.get(roomId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        BinaryMessage message = new BinaryMessage(formattedMessage);
        int successCount = 0;

        for (WebSocketSession s : sessions) {
            // 排除发送者自己
            if (s.isOpen() && !s.getId().equals(senderId)) {
                try {
                    s.sendMessage(message);
                    successCount++;
                } catch (IOException e) {
                    log.error("❌ 发送消息失败: {}", e.getMessage());
                }
            }
        }

        if (successCount > 0) {
            log.debug("📤 房间 {} 转发消息给 {} 个本地用户", roomId, successCount);
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

    /**
     * 校验权限
     * @param principal
     * @param targetPerm
     * @return
     */
    private boolean hasPermission(Principal principal, String targetPerm) {
        // 未登录直接拒绝
        if (principal == null) {
            return false;
        }
        if (principal instanceof Authentication) {
            Authentication auth = (Authentication) principal;
            // 获取所有权限
            Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
            if (authorities == null || authorities.isEmpty()) {
                return false;
            }
            // 遍历权限
            for (GrantedAuthority authority : authorities) {
                String myPerm = authority.getAuthority();
                if (AuthorityEnums.ADMIN.getValue().equals(myPerm)) {
                    // 超级管理员直接放行
                    return true;
                }
                if (myPerm.equals(targetPerm)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断是否为管理员
     * @param auth
     * @return
     */
    private boolean isAdmin(Authentication auth) {
        if (auth == null) {
            return false;
        }
        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        if (authorities == null || authorities.isEmpty()) {
            return false;
        }
        for (GrantedAuthority authority : authorities) {
            if (AuthorityEnums.ADMIN.getValue().equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
