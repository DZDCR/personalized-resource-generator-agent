package com.example.learning.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 行为埋点服务：接收前端学习行为事件并入库（异步）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryService {

    public void record(Long studentId, String eventType, Object payload) {
        log.debug("telemetry student={} event={} at {}", studentId, eventType, Instant.now());
        // TODO: 写入 LearningEventEntity（建议经 RabbitMQ 异步落库）
    }
}