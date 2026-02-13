package com.wfh.drawio.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.*;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.JWTPayload;
import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.exception.BusinessException;
import com.wfh.drawio.mapper.SysRoleMapper;
import com.wfh.drawio.mapper.UserMapper;
import com.wfh.drawio.model.dto.user.UserAddRequest;
import com.wfh.drawio.model.dto.user.UserQueryRequest;
import com.wfh.drawio.model.entity.SysAuthority;
import com.wfh.drawio.model.entity.SysRoleAuthorityRel;
import com.wfh.drawio.model.entity.SysUserRoleRel;
import com.wfh.drawio.model.entity.User;
import com.wfh.drawio.model.enums.UserRoleEnum;
import com.wfh.drawio.model.vo.LoginUserVO;
import com.wfh.drawio.model.vo.RoleAuthorityFlatVO;
import com.wfh.drawio.model.vo.RoleWithAuthoritiesVO;
import com.wfh.drawio.model.vo.UserVO;
import com.wfh.drawio.service.SysRoleAuthorityRelService;
import com.wfh.drawio.service.SysUserRoleRelService;
import com.wfh.drawio.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

/**
 * 用户服务实现
 *
 * @author wangfenghuan
 * @from wangfenghuan
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private PasswordEncoder passwordEncoder;


    @Resource
    private AuthenticationManager authenticationManager;

    @Resource
    private SysRoleMapper sysRoleMapper;

    @Resource
    private SecurityContextRepository securityContextRepository;

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword, String userName) {
        synchronized (userAccount.intern()) {
            // 账户不能重复
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userAccount", userAccount);
            long count = this.baseMapper.selectCount(queryWrapper);
            if (count > 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
            }
            String encodePassword = passwordEncoder.encode(userPassword);
            // 3. 插入数据
            User user = new User();
            user.setUserAccount(userAccount);
            user.setUserPassword(encodePassword);
            user.setUserName(userName);
            boolean saveResult = this.save(user);
            if (!saveResult) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
            }
            return user.getId();
        }
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request, HttpServletResponse response) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }

        // 2. 使用 Spring Security 进行认证
        // 如果密码错误，这里会直接抛出 AuthenticationException，会被全局异常捕获
        Authentication authenticationToken = new UsernamePasswordAuthenticationToken(userAccount, userPassword);
        Authentication authentication = authenticationManager.authenticate(authenticationToken);

        // 3. 构建 SecurityContext
        // 最佳实践：创建一个空的 Context，而不是直接 getContext()，防止多线程竞争干扰
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        // 4. 【核心修复】显式保存 Context 到 Session (Redis)
        // 因为你在 Config 里配置了 requireExplicitSave(true)，没有这一行，Redis 里永远是空的！
        securityContextRepository.saveContext(context, request, response);

        // 5. 从认证结果中获取用户信息
        User user = (User) authentication.getPrincipal();
        LoginUserVO loginUserVO = getLoginUserVO(user);
        
        // 使用 Hutool 生成 JWT Token
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", user.getId());
        // 7天过期
        payload.put(JWTPayload.EXPIRES_AT, System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 7);
        
        String token = JWTUtil.createToken(payload, "wfh-drawio-jwt-secret".getBytes());
        loginUserVO.setToken(token);
        
        return loginUserVO;
    }


    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 从 SecurityContext 获取认证信息
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        return (User) authentication.getPrincipal();
    }

    /**
     * 获取当前登录用户（允许未登录）
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUserPermitNull(HttpServletRequest request) {
        // 从 SecurityContext 获取认证信息
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof User currentUser)) {
            return null;
        }

        if (currentUser.getId() == null) {
            return null;
        }

        // 从数据库查询最新数据
        long userId = currentUser.getId();
        return this.getById(userId);
    }

    /**
     * 是否为管理员
     *
     * @param request
     * @return
     */
    @Override
    public boolean isAdmin(HttpServletRequest request) {
        // 从 SecurityContext 获取认证信息
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
            return false;
        }

        User user = (User) authentication.getPrincipal();
        return isAdmin(user);
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

    /**
     * 用户注销
     *
     * @param request
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null){
            session.invalidate();
        }
        // 清除 SecurityContext 中的认证信息
        SecurityContextHolder.clearContext();
        return true;
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO vo = BeanUtil.copyProperties(user, LoginUserVO.class);
        vo.setUserName(user.getUserName());
        return vo;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = BeanUtil.copyProperties(user, UserVO.class);
        userVO.setAuthorities(user.getAuthoritieList());
        userVO.setUserName(user.getUserName());
        return userVO;
    }

    @Override
    public List<UserVO> getUserVO(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String unionId = userQueryRequest.getUnionId();
        String mpOpenId = userQueryRequest.getMpOpenId();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(id != null, "id", id);
        queryWrapper.eq(StringUtils.isNotBlank(unionId), "unionId", unionId);
        queryWrapper.eq(StringUtils.isNotBlank(mpOpenId), "mpOpenId", mpOpenId);
        queryWrapper.eq(StringUtils.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StringUtils.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.like(StringUtils.isNotBlank(userName), "userName", userName);
        return queryWrapper;
    }

    /**
     * 管理员创建用户
     */
    @Override
    public Long addUserByAdmin(UserAddRequest userAddRequest) {
        if (userAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtils.copyProperties(userAddRequest, user);
        // 设置默认密码并加密
        String defaultPassword = "12345678";
        user.setUserPassword(passwordEncoder.encode(defaultPassword));
        boolean result = this.save(user);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建用户失败");
        }
        return user.getId();
    }

    @NotNull
    @Override
    public List<RoleWithAuthoritiesVO> getRoleWithAuthoritiesVOS() {
        List<RoleAuthorityFlatVO> roleAuthorityFlatVO = sysRoleMapper.selectRoleWithAuthoritiesFlat();
        // 按照roleId分组
        Map<Long, List<SysAuthority>> authMap = roleAuthorityFlatVO.stream()
                .filter(roleAuthorityFlat -> roleAuthorityFlat.getAuthorityId() != null)
                .collect(Collectors.groupingBy(RoleAuthorityFlatVO::getRoleId, Collectors.mapping(this::toAuthority, Collectors.toList())));
        List<RoleWithAuthoritiesVO> resList = roleAuthorityFlatVO.stream()
                .collect(Collectors.toMap(RoleAuthorityFlatVO::getRoleId, this::buildRoleVO, (v1, v2) -> v1, LinkedHashMap::new))
                .values()
                .stream()
                .peek(vo -> vo.setAuthorities(authMap.getOrDefault(vo.getId(), Collections.emptyList())))
                .toList();
        return resList;
    }

    private RoleWithAuthoritiesVO buildRoleVO(RoleAuthorityFlatVO flat) {
        RoleWithAuthoritiesVO vo = new RoleWithAuthoritiesVO();
        vo.setId(flat.getRoleId());
        vo.setRoleName(flat.getRoleName());
        vo.setDescription(flat.getRoleDescription());
        vo.setCreateTime(flat.getRoleCreateTime());
        vo.setUpdateTime(flat.getRoleUpdateTime());
        return vo;
    }

    private SysAuthority toAuthority(RoleAuthorityFlatVO flat) {
        SysAuthority auth = new SysAuthority();
        auth.setId(flat.getAuthorityId());
        auth.setParentId(flat.getParentId());
        auth.setName(flat.getAuthorityName());
        auth.setDescription(flat.getAuthorityDescription());
        auth.setAuthority(flat.getAuthority());
        auth.setType(flat.getType());
        auth.setCreateTime(flat.getAuthorityCreateTime());
        auth.setUpdateTime(flat.getAuthorityUpdateTime());
        return auth;
    }

    @Resource
    private SysUserRoleRelService sysUserRoleRelService;

    @Resource
    private SysRoleAuthorityRelService sysRoleAuthorityRelService;

    @Override
    public boolean updateUserRoles(Long userId, List<Long> roleIds) {
        // 1. 校验参数
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        }
        if (roleIds == null) {
            roleIds = new ArrayList<>();
        }

        // 2. 查询用户是否存在
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }

        // 3. 删除用户原有的所有角色关联
        QueryWrapper<SysUserRoleRel> deleteWrapper =
            new QueryWrapper<>();
        deleteWrapper.eq("userId", userId);
        sysUserRoleRelService.remove(deleteWrapper);

        // 4. 如果没有新角色，直接返回
        if (CollUtil.isEmpty(roleIds)) {
            return true;
        }

        // 5. 批量插入新的角色关联
        List<SysUserRoleRel> userRoleList = new ArrayList<>();
        for (Long roleId : roleIds) {
            SysUserRoleRel userRoleRel = new com.wfh.drawio.model.entity.SysUserRoleRel();
            userRoleRel.setUserId(userId);
            userRoleRel.setRoleId(roleId);
            userRoleList.add(userRoleRel);
        }

        return sysUserRoleRelService.saveBatch(userRoleList);
    }

    @Override
    public boolean updateRoleAuthorities(Long roleId, List<Long> authorityIds) {
        // 1. 校验参数
        if (roleId == null || roleId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "角色ID不能为空");
        }
        if (authorityIds == null) {
            authorityIds = new ArrayList<>();
        }

        // 2. 查询角色是否存在
        com.wfh.drawio.model.entity.SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "角色不存在");
        }

        // 3. 删除角色原有的所有权限关联
        QueryWrapper<SysRoleAuthorityRel> deleteWrapper =
            new QueryWrapper<>();
        deleteWrapper.eq("roleId", roleId);
        sysRoleAuthorityRelService.remove(deleteWrapper);

        // 4. 如果没有新权限，直接返回
        if (CollUtil.isEmpty(authorityIds)) {
            return true;
        }
        // 5. 批量插入新的权限关联
        List<SysRoleAuthorityRel> roleAuthList = new ArrayList<>();
        for (Long authorityId : authorityIds) {
            SysRoleAuthorityRel roleAuthRel = new com.wfh.drawio.model.entity.SysRoleAuthorityRel();
            roleAuthRel.setRoleId(roleId);
            roleAuthRel.setAuthorityId(authorityId);
            roleAuthList.add(roleAuthRel);
        }
        return sysRoleAuthorityRelService.saveBatch(roleAuthList);
    }
}
