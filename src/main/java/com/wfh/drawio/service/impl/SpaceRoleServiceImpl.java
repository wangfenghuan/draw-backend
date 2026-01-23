package com.wfh.drawio.service.impl;

import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.exception.BusinessException;
import com.wfh.drawio.mapper.SysRoleMapper;
import com.wfh.drawio.model.entity.Space;
import com.wfh.drawio.model.entity.SysAuthority;
import com.wfh.drawio.model.entity.SysRole;
import com.wfh.drawio.model.entity.SysRoleAuthorityRel;
import com.wfh.drawio.model.enums.RoleEnums;
import com.wfh.drawio.service.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 空间角色服务实现类
 *
 * @author wangfenghuan
 */
@Slf4j
@Service
public class SpaceRoleServiceImpl implements SpaceRoleService {

    @Resource
    private SysRoleMapper sysRoleMapper;

    @Resource
    private SysRoleAuthorityRelService sysRoleAuthorityRelService;

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SysAuthorityService sysAuthorityService;

    @Override
    public List<SysAuthority> getAuthoritiesBySpaceRole(String spaceRole) {
        if (ObjUtil.isEmpty(spaceRole)) {
            return Collections.emptyList();
        }

        // 将空间角色转换为角色枚举
        RoleEnums roleEnum = RoleEnums.getEnumByValue(spaceRole);
        if (roleEnum == null) {
            log.warn("未找到对应的角色枚举: {}", spaceRole);
            return Collections.emptyList();
        }

        return getAuthoritiesByRoleEnum(roleEnum);
    }

    @Override
    public List<SysAuthority> getAuthoritiesByRoleEnum(RoleEnums roleEnum) {
        if (roleEnum == null) {
            return Collections.emptyList();
        }

        // 根据角色名称查询角色
        SysRole sysRole = sysRoleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getName, roleEnum.getValue())
        );

        if (sysRole == null) {
            log.warn("未找到角色: {}", roleEnum.getValue());
            return Collections.emptyList();
        }

        // 查询角色关联的权限
        List<SysRoleAuthorityRel> roleAuthorityRels = sysRoleAuthorityRelService.lambdaQuery()
                .eq(SysRoleAuthorityRel::getRoleId, sysRole.getId())
                .list();

        if (roleAuthorityRels.isEmpty()) {
            log.warn("角色 {} 没有配置任何权限", roleEnum.getValue());
            return Collections.emptyList();
        }

        // 获取权限ID列表
        List<Long> authorityIds = roleAuthorityRels.stream()
                .map(SysRoleAuthorityRel::getAuthorityId)
                .collect(Collectors.toList());

        // 根据权限ID列表查询权限详情
        List<SysAuthority> authorities = sysAuthorityService.lambdaQuery()
                .in(SysAuthority::getId, authorityIds)
                .list();

        log.info("角色 {} 拥有 {} 个权限", roleEnum.getValue(), authorities.size());
        return authorities;
    }

    @Override
    public boolean hasAuthority(Long userId, Long spaceId, String authority) {
        if (ObjUtil.hasEmpty(userId, spaceId, authority)) {
            return false;
        }
        // 查询该空间是团队还是私有还是公共
        Space space = spaceService.getById(spaceId);
        Integer spaceType = space.getSpaceType();
        // 如果是团队空间才走团队空间的权限校验逻辑
        if (spaceType == 1){
            // 查询用户在空间中的角色
            var spaceUser = spaceUserService.lambdaQuery()
                    .eq(com.wfh.drawio.model.entity.SpaceUser::getSpaceId, spaceId)
                    .eq(com.wfh.drawio.model.entity.SpaceUser::getUserId, userId)
                    .one();

            if (spaceUser == null) {
                return false;
            }

            // 获取角色对应的权限列表
            List<SysAuthority> authorities = getAuthoritiesBySpaceRole(spaceUser.getSpaceRole());

            // 检查是否包含指定权限
            return authorities.stream()
                    .anyMatch(auth -> auth.getAuthority().equals(authority));
        } else if (spaceType == 0) {
            // 私有空间，校验是否是创建者即可
            return space.getUserId().equals(userId);
        }
        return true;
    }

    @Override
    public boolean hasAnyAuthority(Long userId, Long spaceId, String... authorities) {
        if (ObjUtil.hasEmpty(userId, spaceId, authorities)) {
            return false;
        }

        // 查询用户在空间中的角色
        var spaceUser = spaceUserService.lambdaQuery()
                .eq(com.wfh.drawio.model.entity.SpaceUser::getSpaceId, spaceId)
                .eq(com.wfh.drawio.model.entity.SpaceUser::getUserId, userId)
                .one();

        if (spaceUser == null) {
            return false;
        }

        // 获取角色对应的权限列表
        List<SysAuthority> userAuthorities = getAuthoritiesBySpaceRole(spaceUser.getSpaceRole());

        // 检查是否包含任意一个指定权限
        for (String authority : authorities) {
            boolean hasAuth = userAuthorities.stream()
                    .anyMatch(auth -> auth.getAuthority().equals(authority));
            if (hasAuth) {
                return true;
            }
        }

        return false;
    }
}
