package com.example.learning.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI ChatClient 配置。
 * heavy 用于长文档类 Agent，light 用于画像/短对话类 Agent。
 */
@Configuration
public class LlmConfig {

    @Bean
    public ChatClient lightChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public ChatClient heavyChatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}