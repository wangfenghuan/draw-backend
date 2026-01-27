package com.wfh.drawio.controller;

import cn.hutool.jwt.JWTUtil;

import com.wfh.drawio.common.BaseResponse;
import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.common.ResultUtils;
import com.wfh.drawio.exception.BusinessException;
import com.wfh.drawio.model.entity.RoomSnapshots;
import com.wfh.drawio.model.entity.User;
import com.wfh.drawio.security.RoomSecurityService;
import com.wfh.drawio.service.RoomSnapshotsService;
import com.wfh.drawio.service.RoomUpdatesService;
import com.wfh.drawio.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * 内部调用接口 (供 Node.js 协作服务使用)
 *
 * @author fenghuanwang
 */
@RestController
@RequestMapping("/internal")
@Slf4j
public class InternalApiController {

    @Resource
    private UserService userService;

    @Resource
    private RoomSecurityService roomSecurityService;

    @Resource
    private RoomSnapshotsService snapshotsService;

    @Resource
    private RoomUpdatesService updatesService;

    @Value("${drawio.internal-token}")
    private String internalToken;

    /**
     * 鉴权检查 (Node.js -> Spring Boot)
     * 利用 Cookie 检查用户身份，并返回权限
     */
    @PostMapping("/auth")
    @Operation(summary = "内部鉴权", description = "供 Node.js 调用，校验 WebSocket 连接权限")
    public BaseResponse<AuthResponse> checkAuth(@RequestBody AuthRequest authRequest, HttpServletRequest request) {
        String token = authRequest.getToken();
        if (StringUtils.isBlank(token)) {
             // 兼容 Header
             String authHeader = request.getHeader("Authorization");
             if (StringUtils.isNotBlank(authHeader) && authHeader.startsWith("Bearer ")) {
                 token = authHeader.substring(7);
             }
        }

        if (StringUtils.isBlank(token)) {
            return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR);
        }

        // 1. JWT 校验
        boolean verify = false;
        try {
            verify = JWTUtil.verify(token, "wfh-drawio-jwt-secret".getBytes());
        } catch (Exception e) {
            log.error("JWT Verify Error", e);
        }
        
        if (!verify) {
            return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, "Token Invalid");
        }

        // 2. 解析 UserID
        Long userId = null;
        try {
            Object userIdObj = JWTUtil.parseToken(token).getPayload("userId");
            userId = Long.valueOf(userIdObj.toString());
        } catch (Exception e) {
            return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, "Token Parse Error");
        }
        
        // 3. 获取用户信息 (从数据库查，确保权限最新)
        User loginUser = userService.getById(userId);
        if (loginUser == null) {
            return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, "User Not Found");
        }

        String roomId = authRequest.getRoomId();
        if (StringUtils.isBlank(roomId)) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }

        // 2. 检查 RBAC 权限
        // checkRoomPermission(userId, roomId, checkEdit)
        // 先检查只读权限
        boolean canView = roomSecurityService.checkRoomPermission(loginUser.getId(), roomId, false);
        if (!canView) {
            return ResultUtils.error(ErrorCode.NO_AUTH_ERROR);
        }

        // 检查编辑权限
        boolean canEdit = roomSecurityService.checkRoomPermission(loginUser.getId(), roomId, true);

        // 3. 构建返回
        AuthResponse response = new AuthResponse();
        response.setUserId(loginUser.getId());
        response.setNickname(loginUser.getUserName()); // 假设 User 有 getUserName
        response.setAvatarUrl(loginUser.getUserAvatar());
        // Simple permission string: "READ_WRITE" or "READ_ONLY"
        response.setPermission(canEdit ? "READ_WRITE" : "READ_ONLY");

        return ResultUtils.success(response);
    }

    /**
     * 保存快照 (Node.js -> Spring Boot)
     * 使用 API Key 鉴权
     */
    @PostMapping("/save")
    @Operation(summary = "内部保存", description = "供 Node.js 回调保存图表数据")
    public BaseResponse<Boolean> saveSnapshot(@RequestBody SaveRequest saveRequest, HttpServletRequest request) {
        // 1. API Key 校验
        String token = request.getHeader("X-Internal-Token");
        if (!internalToken.equals(token)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "Invalid Internal Token");
        }

        Long roomId = saveRequest.getRoomId();
        String xml = saveRequest.getXml();

        if (roomId == null || StringUtils.isBlank(xml)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 2. 保存到数据库
        RoomSnapshots roomSnapshots = new RoomSnapshots();
        // 这里的 xml 可能是 String，entity 中是 String
        roomSnapshots.setSnapshotData(xml);
        roomSnapshots.setRoomId(roomId);
        // lastUpdateId 目前 Node.js 端可能还没法准确传，暂置0或由 Node 传
        roomSnapshots.setLastUpdateId(saveRequest.getLastUpdateId() != null ? saveRequest.getLastUpdateId() : 0L);

        boolean b = snapshotsService.saveOrUpdate(roomSnapshots);
        
        // 3. 异步清理：只保留最近的 20 个快照
        try {
             snapshotsService.cleanOldSnapshots(roomId);
        } catch (Exception e) {
            log.error("Clean snapshots failed", e);
        }

        return ResultUtils.success(b);
    }

    // DTOs
    @Data
    public static class AuthRequest {
        private String roomId;
        private String token;
    }

    @Data
    public static class AuthResponse {
        private Long userId;
        private String nickname;
        private String avatarUrl;
        private String permission;
    }

    @Data
    public static class SaveRequest {
        private Long roomId;
        private String xml;
        private Long lastUpdateId;
    }
}
