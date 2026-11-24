package com.example.learning.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WebSocket 配置：任务进度实时推送端点。
 * 客户端连接 ws://host/ws/progress?taskId=xxx
 */
@Slf4j
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final TaskProgressBroadcaster broadcaster;
    private final ImWebSocketHandler imWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(createHandler(), "/ws/progress")
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                            WebSocketHandler wsHandler, Map<String, Object> attributes) {
                        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(request.getURI());
                        String taskId = builder.build().getQueryParams().getFirst("taskId");
                        attributes.put("taskId", taskId);
                        return true;
                    }
                    @Override
                    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                            WebSocketHandler wsHandler, Exception exception) {
                    }
                })
                .setAllowedOrigins("*");

        // 实时聊天端点：ws://host/ws/im?studentId=1
        // 握手时把 studentId 放进 session attributes，后续 Handler 直接读取，避免每条消息重复传参。
        registry.addHandler(imWebSocketHandler, "/ws/im")
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                            WebSocketHandler wsHandler, Map<String, Object> attributes) {
                        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(request.getURI());
                        String studentId = builder.build().getQueryParams().getFirst("studentId");
                        if (studentId != null) {
                            try {
                                attributes.put("studentId", Long.parseLong(studentId));
                            } catch (NumberFormatException e) {
                                log.warn("invalid studentId in ws handshake: {}", studentId);
                            }
                        }
                        return true;
                    }

                    @Override
                    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                            WebSocketHandler wsHandler, Exception exception) {
                    }
                })
                .setAllowedOrigins("*");
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(64 * 1024);
        container.setMaxBinaryMessageBufferSize(64 * 1024);
        container.setMaxSessionIdleTimeout(120_000L);
        return container;
    }

    private ProgressWebSocketHandler createHandler() {
        return new ProgressWebSocketHandler(broadcaster);
    }
}