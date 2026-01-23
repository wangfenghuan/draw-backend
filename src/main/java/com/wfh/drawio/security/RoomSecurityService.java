package com.wfh.drawio.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wfh.drawio.mapper.DiagramRoomMapper;
import com.wfh.drawio.mapper.RoomMemberMapper;
import com.wfh.drawio.mapper.SpaceUserMapper;
import com.wfh.drawio.model.entity.DiagramRoom;
import com.wfh.drawio.model.entity.RoomMember;
import com.wfh.drawio.model.entity.Space;
import com.wfh.drawio.model.entity.SpaceUser;
import com.wfh.drawio.model.enums.AuthorityEnums;
import com.wfh.drawio.model.enums.SpaceTypeEnum;
import com.wfh.drawio.service.RoomRoleService;
import com.wfh.drawio.service.SpaceService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * 房间权限服务
 * 用于在 Security 表达式和 WebSocket 中调用
 *
 * @author wangfenghuan
 */
@Slf4j
@Component
public class RoomSecurityService {

    @Resource
    private RoomRoleService roomRoleService;

    @Resource
    private DiagramRoomMapper diagramRoomMapper;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserMapper spaceUserMapper;

    @Resource
    private RoomMemberMapper roomMemberMapper;

    /**
     * 检查用户在指定房间是否具有指定权限
     *
     * @param roomId 房间ID
     * @param authority 权限标识
     * @return 是否具有权限
     */
    public boolean hasRoomAuthority(Long roomId, String authority) {
        if (roomId == null || authority == null) {
            return false;
        }

        // 获取当前认证信息
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        // 从认证信息中获取用户ID
        Long userId = extractUserId(authentication.getPrincipal());

        if (userId == null) {
            return false;
        }

        return roomRoleService.hasAuthority(userId, roomId, authority);
    }

    /**
     * 检查用户在指定房间是否具有任意一个指定权限
     *
     * @param roomId 房间ID
     * @param authorities 权限标识数组
     * @return 是否具有任意一个权限
     */
    public boolean hasAnyRoomAuthority(Long roomId, String... authorities) {
        if (roomId == null || authorities == null || authorities.length == 0) {
            return false;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Long userId = extractUserId(authentication.getPrincipal());
        if (userId == null) {
            return false;
        }

        return roomRoleService.hasAnyAuthority(userId, roomId, authorities);
    }

    /**
     * 检查用户对协作房间的完整权限（包括空间成员资格和房间角色）
     * 用于 WebSocket 连接和消息处理的权限检查
     *
     * 权限模型：
     * 1. 先检查空间成员资格（如果是私有空间）
     * 2. 再检查协作房间的角色权限
     *
     * @param userId 用户ID
     * @param roomName 房间名称/ID
     * @param checkEdit true=检查编辑权限，false=检查查看权限
     * @return 是否有权限
     */
    public boolean checkRoomPermission(Long userId, String roomName, boolean checkEdit) {
        try {
            // 1. 解析房间 ID
            Long roomId = Long.valueOf(roomName);

            // 2. 查询房间信息
            DiagramRoom room = diagramRoomMapper.selectById(roomId);
            if (room == null) {
                log.warn("⚠️ 房间不存在: {}", roomId);
                return false;
            }

            // 3. 检查是否是超级管理员（需要查询用户权限）
            if (isAdmin(userId)) {
                return true;
            }

            // 4. 检查空间成员资格（如果是私有空间）
            Long spaceId = room.getSpaceId();
            if (spaceId != null) {
                // 查询空间信息
                Space space = spaceService.getById(spaceId);
                if (space == null) {
                    log.warn("⚠️ 空间不存在: {}", spaceId);
                    return false;
                }

                // 私有空间：必须是空间成员
                if (SpaceTypeEnum.PRIVATE.getValue() == space.getSpaceType()) {
                    if (!space.getUserId().equals(userId)) {
                        log.warn("⚠️ 用户 {} 无权访问私有空间 {}", userId, spaceId);
                        return false;
                    }
                } else if (SpaceTypeEnum.TEAM.getValue() == space.getSpaceType()) {
                    // 团队空间：必须是空间成员
                    SpaceUser spaceUser = spaceUserMapper.selectOne(
                        new LambdaQueryWrapper<SpaceUser>()
                            .eq(SpaceUser::getSpaceId, spaceId)
                            .eq(SpaceUser::getUserId, userId)
                    );
                    if (spaceUser == null) {
                        log.warn("⚠️ 用户 {} 不是团队空间 {} 的成员", userId, spaceId);
                        return false;
                    }
                }
            }

            // 5. 查询用户在协作房间的角色
            RoomMember roomMember = roomMemberMapper.selectOne(
                new LambdaQueryWrapper<RoomMember>()
                    .eq(RoomMember::getRoomId, roomId)
                    .eq(RoomMember::getUserId, userId)
            );

            // 如果没有房间角色记录，检查是否是房主
            if (roomMember == null) {
                if (room.getOwnerId().equals(userId)) {
                    // 房主拥有所有权限
                    return true;
                }
                log.warn("⚠️ 用户 {} 不是房间 {} 的成员", userId, roomId);
                return false;
            }

            // 6. 根据协作房间角色判断权限
            String roomRole = roomMember.getRoomRole();
            if (roomRole == null) {
                log.warn("⚠️ 用户 {} 在房间 {} 中的角色为空", userId, roomId);
                return false;
            }

            // 协作房间角色权限映射
            boolean hasViewPermission = false;
            boolean hasEditPermission = false;

            switch (roomRole.toLowerCase()) {
                case "diagram_admin":
                case "diagram_editor":
                    hasViewPermission = true;
                    hasEditPermission = true;
                    break;
                case "diagram_viewer":
                    hasViewPermission = true;
                    hasEditPermission = false;
                    break;
                default:
                    log.warn("⚠️ 未知的协作房间角色: {}", roomRole);
                    return false;
            }

            return checkEdit ? hasEditPermission : hasViewPermission;

        } catch (NumberFormatException e) {
            log.warn("⚠️ 房间 ID 格式错误: {}", roomName);
            return false;
        } catch (Exception e) {
            log.error("❌ 检查房间权限失败: room={}", roomName, e);
            return false;
        }
    }

    /**
     * 检查用户是否是管理员
     *
     * @param userId 用户ID
     * @return 是否是管理员
     */
    public boolean isAdmin(Long userId) {
        // 这里需要查询用户的权限，看是否有 admin 权限
        // 由于我们在 WebSocket 中没有完整的用户对象，暂时返回 false
        // 如果需要，可以添加用户权限查询逻辑
        return false;
    }

    /**
     * 检查用户是否是管理员（基于 Authentication）
     *
     * @param authentication Spring Security 认证对象
     * @return 是否是管理员
     */
    public boolean isAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        if (authorities == null) {
            return false;
        }

        return authorities.stream()
                .anyMatch(auth -> AuthorityEnums.ADMIN.getValue().equals(auth.getAuthority()));
    }

    /**
     * 从 Principal 中提取用户ID
     *
     * @param principal Spring Security Principal
     * @return 用户ID
     */
    private Long extractUserId(Object principal) {
        if (principal instanceof com.wfh.drawio.model.entity.User user) {
            return user.getId();
        } else if (principal instanceof org.springframework.security.core.userdetails.User springUser) {
            // 如果是默认的 User 实现,从用户名中提取 ID
            try {
                return Long.parseLong(springUser.getUsername());
            } catch (NumberFormatException e) {
                return null;
            }
        } else if (principal instanceof String) {
            // 如果用户名直接是 String
            try {
                return Long.parseLong((String) principal);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
