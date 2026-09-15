package com.example.learning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 线程池配置：多智能体并行生成使用虚拟线程执行器。
 */
@Configuration
public class AsyncConfig {

    /** 资源生成长任务执行器（虚拟线程，Java 21） */
    @Bean("resourceExecutor")
    public Executor resourceExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}