package com.example.learning.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
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

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(createHandler(), "/ws/progress")
                .setHandshakeHandler(new DefaultHandshakeHandler() {
                    @Override
                    protected Map<String, Object> getAttributes(
                            org.springframework.http.server.ServerHttpRequest request) {
                        Map<String, Object> attrs = new java.util.HashMap<>(super.getAttributes(request));
                        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(request.getURI());
                        String taskId = builder.build().getQueryParams().getFirst("taskId");
                        attrs.put("taskId", taskId);
                        return attrs;
                    }
                })
                .setAllowedOrigins("*");
    }

    private ProgressWebSocketHandler createHandler() {
        return new ProgressWebSocketHandler(broadcaster);
    }
}