package com.example.learning.auth.service;

import com.example.learning.auth.dto.AuthViews.SendCodeView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 开发环境“邮件/短信发件箱”。生产环境由 SMTP / 短信网关替换，
 * 不应把验证码回显给客户端。
 */
@Service
@RequiredArgsConstructor
public class DevOutboxService {

    private static final int MAX_MESSAGES = 50;
    private final Deque<DevMessage> messages = new ArrayDeque<>();

    public synchronized void record(String channel, String target, String scene, String code, Instant expiresAt) {
        messages.addFirst(new DevMessage(channel, target, scene, code, expiresAt, Instant.now()));
        while (messages.size() > MAX_MESSAGES) {
            messages.removeLast();
        }
    }

    public synchronized List<DevMessage> list() {
        return new ArrayList<>(messages);
    }

    public record DevMessage(String channel, String target, String scene, String code,
                             Instant expiresAt, Instant createdAt) {
    }

    /** 便于控制器判断是否允许暴露发件箱。 */
    public SendCodeView view(String message, String debugCode, int expiresIn) {
        return new SendCodeView(true, message, debugCode, expiresIn);
    }
}
