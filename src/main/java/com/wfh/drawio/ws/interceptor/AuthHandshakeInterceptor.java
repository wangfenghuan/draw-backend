package com.wfh.drawio.ws.interceptor;

import com.wfh.drawio.model.entity.User;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * @Title: AuthHandshakeInterceptor
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ws.interceptor
 * @Date 2025/12/27 14:37
 * @description:
 */
@Component
public class AuthHandshakeInterceptor implements HandshakeInterceptor {


    @Override
    public boolean beforeHandshake(@NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response, @NotNull WebSocketHandler wsHandler, @NotNull Map<String, Object> attributes) throws Exception {
        // 从 SecurityContext 获取认证信息
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
            return false;
        }

        User loginUser = (User) authentication.getPrincipal();
        if (loginUser == null || loginUser.getId() == null) {
            return false;
        }

        attributes.put("user", loginUser);
        return true;
    }

    @Override
    public void afterHandshake(@NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response, @NotNull WebSocketHandler wsHandler, Exception exception) {

    }
}
