package com.wfh.drawio.ws.interceptor;

import com.wfh.drawio.model.entity.User;
import com.wfh.drawio.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
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

    @Resource
    private UserService userService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
        User loginUser = userService.getLoginUser(servletRequest);
        if (loginUser == null){
            return false;
        }
        attributes.put("userId", loginUser.getId());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {

    }
}
