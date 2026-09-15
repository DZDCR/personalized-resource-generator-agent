package com.example.learning.agent;

import com.example.learning.config.PromptLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Optional;

/**
 * 智能体抽象基类。
 * 统一提供：Prompt 模板加载、LLM 调用（文本/结构化）、失败重试与兜底。
 *
 * <p>约定：
 * <ul>
 *   <li>每个 Agent 一个 Spring Bean，负责单一职责的产物生成。</li>
 *   <li>LLM 调用统一经过 callEntity / callText，失败返回 empty 不抛异常，由编排层兜底。</li>
 * </ul>
 */
@Slf4j
public abstract class BaseAgent {

    protected final ChatClient chatClient;
    protected final PromptLoader promptLoader;

    protected BaseAgent(ChatClient.Builder chatClientBuilder, PromptLoader promptLoader) {
        this.chatClient = chatClientBuilder.build();
        this.promptLoader = promptLoader;
    }

    /** 智能体名称（日志/任务进度中标识） */
    public abstract String name();

    protected String template(String name) {
        return promptLoader.load(name);
    }

    /**
     * 调用 LLM 并结构化解析为指定类型（Spring AI 内置 JSON Schema 校验）。
     */
    protected <T> Optional<T> callEntity(String promptText, Class<T> type) {
        try {
            T result = chatClient.prompt().user(promptText).call().entity(type);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            log.warn("agent[{}] structured call failed: {}", name(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 调用 LLM 获取纯文本。
     */
    protected Optional<String> callText(String promptText) {
        try {
            String result = chatClient.prompt().user(promptText).call().content();
            return Optional.ofNullable(result);
        } catch (Exception e) {
            log.warn("agent[{}] text call failed: {}", name(), e.getMessage());
            return Optional.empty();
        }
    }
}