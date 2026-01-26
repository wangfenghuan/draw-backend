package com.wfh.drawio.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.exception.BusinessException;
import com.wfh.drawio.model.entity.User;
import com.wfh.drawio.service.OAuth2UserSyncService;
import com.wfh.drawio.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OAuth2 用户同步服务实现
 * 负责将OAuth2登录的用户（如GitHub）同步到本地数据库
 *
 * @author fenghuanwang
 */
@Service
@Slf4j
public class OAuth2UserSyncServiceImpl implements OAuth2UserSyncService {

    @Resource
    @Lazy
    private UserService userService;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User syncOrCreateUser(OAuth2User oAuth2User, String provider) {
        if (oAuth2User == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "OAuth2用户信息不能为空");
        }
        // GitHub使用"login"属性作为用户名
        String githubLogin = oAuth2User.getAttribute("login");
        String name = oAuth2User.getAttribute("name");
        String email = oAuth2User.getAttribute("email");
        String avatarUrl = oAuth2User.getAttribute("avatar_url");
        String bio = oAuth2User.getAttribute("bio");
        if (githubLogin == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "无法获取GitHub用户名");
        }
        log.info("开始同步OAuth2用户: githubLogin={}, name={}, email={}", githubLogin, name, email);
        // 查找是否已存在该用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUserAccount, githubLogin);
        User existingUser = userService.getOne(wrapper);

        if (existingUser != null) {
            // 更新用户信息
            boolean needUpdate = false;
            if (name != null && !name.equals(existingUser.getUserName())) {
                existingUser.setUserName(name);
                needUpdate = true;
            }
            if (avatarUrl != null && !avatarUrl.equals(existingUser.getUserAvatar())) {
                existingUser.setUserAvatar(avatarUrl);
                needUpdate = true;
            }
            if (bio != null && !bio.equals(existingUser.getUserProfile())) {
                existingUser.setUserProfile(bio);
                needUpdate = true;
            }
            if (needUpdate) {
                userService.updateById(existingUser);
                log.info("用户信息更新成功: userId={}", existingUser.getId());
            }
            return existingUser;
        }
        // 创建新用户
        User newUser = new User();
        newUser.setUserAccount(githubLogin);
        // 生成随机密码（OAuth2用户不需要密码，但数据库字段非空）
        String randomPassword = RandomUtil.randomString(8);
        newUser.setUserPassword(passwordEncoder.encode(randomPassword));

        // 设置用户信息
        newUser.setUserName(name != null ? name : githubLogin);
        newUser.setUserAvatar(avatarUrl);
        newUser.setUserProfile(bio);

        // 设置默认角色为普通用户
        newUser.setUserRole("user");

        // 保存用户
        boolean saved = userService.save(newUser);
        if (!saved) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建OAuth2用户失败");
        }

        log.info("OAuth2用户创建成功: userId={}, userAccount={}", newUser.getId(), newUser.getUserAccount());
        return newUser;
    }

    @Override
    public User findByGithubLogin(String githubLogin) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUserAccount, githubLogin);
        return userService.getOne(wrapper);
    }
}
