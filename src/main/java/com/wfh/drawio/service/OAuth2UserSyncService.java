package com.wfh.drawio.service;

import com.wfh.drawio.model.entity.User;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * OAuth2 用户同步服务
 * 用于将OAuth2登录的用户（如GitHub）同步到本地数据库
 *
 * @author fenghuanwang
 */
public interface OAuth2UserSyncService {

    /**
     * 同步或创建OAuth2用户
     * 如果用户已存在（通过userAccount查找），则更新用户信息
     * 如果用户不存在，则创建新用户
     *
     * @param oAuth2User OAuth2用户信息
     * @param provider OAuth2提供商（如"github"）
     * @return 本地用户实体
     */
    User syncOrCreateUser(OAuth2User oAuth2User, String provider);

    /**
     * 根据GitHub用户名查找用户
     *
     * @param githubLogin GitHub用户名
     * @return 用户实体，如果不存在返回null
     */
    User findByGithubLogin(String githubLogin);
}
