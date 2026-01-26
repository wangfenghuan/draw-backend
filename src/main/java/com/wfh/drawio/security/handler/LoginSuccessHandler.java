package com.wfh.drawio.security.handler;

import com.wfh.drawio.model.entity.User;
import com.wfh.drawio.service.OAuth2UserSyncService;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * OAuth2 登录成功处理器
 * 处理GitHub等OAuth2登录成功后的逻辑
 *
 * @author fenghuanwang
 */
@Component
@Slf4j
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    @Value("${spring.security.oauth2.client.provider.github.user-name-attribute}")
    private String loginNameAttribute;

    @Resource
    @Lazy
    private OAuth2UserSyncService oauth2UserSyncService;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {

        Object principal = authentication.getPrincipal();
        log.info("OAuth2登录成功，用户信息: {}", principal);

        // 判断是否为OAuth2登录
        if (!(principal instanceof OAuth2User oAuth2User)) {
            log.warn("非OAuth2登录类型，跳过处理");
            return;
        }

        try {
            // 获取GitHub用户名
            String githubLogin = oAuth2User.getAttribute(loginNameAttribute);
            if (githubLogin == null) {
                log.error("无法从OAuth2用户信息中获取用户名");
                throw new RuntimeException("无法获取GitHub用户信息");
            }

            log.info("GitHub用户登录: githubLogin={}", githubLogin);

            // 同步或创建用户
            User user = oauth2UserSyncService.syncOrCreateUser(oAuth2User, "github");

            log.info("GitHub用户登录成功，已同步到本地数据库: userId={}, userAccount={}",
                    user.getId(), user.getUserAccount());

            // 重定向到前端页面
            String redirectUrl = determineRedirectUrl(request);
            response.sendRedirect(redirectUrl);

        } catch (Exception e) {
            log.error("处理OAuth2登录失败", e);
            response.sendRedirect("/login?error=oauth2_failed");
        }
    }

    /**
     * 确定登录成功后的重定向URL
     */
    private String determineRedirectUrl(HttpServletRequest request) {
        // 可以从session中获取登录前访问的页面
        String referer = request.getHeader("Referer");

        if (referer != null && !referer.isEmpty()) {
            log.info("重定向到来源页面: {}", referer);
            return referer;
        }

        // 默认重定向到首页
        String contextPath = request.getContextPath();
        return contextPath + "/";
    }
}
