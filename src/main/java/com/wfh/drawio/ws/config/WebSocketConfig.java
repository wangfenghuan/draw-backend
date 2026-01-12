package com.wfh.drawio.ws.config;

import com.wfh.drawio.ws.handler.ExcalidrawHandler;
import com.wfh.drawio.ws.handler.YjsHandler;
import com.wfh.drawio.ws.interceptor.AuthHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * @Title: WebSocketConfig
 * @Author wangfenghuan
 * @Package com.wfh.drawio.config
 * @Date 2025/12/27 14:17
 * @description:
 */
@EnableWebSocket
@Configuration
public class WebSocketConfig implements WebSocketConfigurer {

    private final YjsHandler YjsHandler;

    private final AuthHandshakeInterceptor authHandshakeInterceptor;

    public WebSocketConfig(YjsHandler YjsHandler, AuthHandshakeInterceptor authHandshakeInterceptor) {
        this.YjsHandler = YjsHandler;
        this.authHandshakeInterceptor = authHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(YjsHandler, "/yjs/*")
                .addInterceptors(authHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
