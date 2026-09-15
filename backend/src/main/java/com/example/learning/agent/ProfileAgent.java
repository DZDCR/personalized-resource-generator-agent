package com.example.learning.agent;

import com.example.learning.config.PromptLoader;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 画像智能体：从对话中抽取特征，更新学生画像的指定维度。
 * 返回结构见 docs/architecture.md 画像部分。
 */
@Component
public class ProfileAgent extends BaseAgent {

    public ProfileAgent(ChatClient.Builder chatClientBuilder, PromptLoader promptLoader) {
        super(chatClientBuilder, promptLoader);
    }

    @Override
    public String name() {
        return "profile";
    }

    /**
     * @param currentProfileJson 当前画像（可能为空）
     * @param latestReply        学生最新一条回复
     * @return 画像抽取结果（reply / field_update / confidence_delta / done）
     */
    public Optional<ProfileUpdate> extract(String currentProfileJson, String latestReply) {
        String tpl = template("profile_extract");
        String prompt = tpl
                .replace("{{profile}}", String.valueOf(currentProfileJson))
                .replace("{{latest_reply}}", latestReply);
        return callEntity(prompt, ProfileUpdate.class);
    }

    /**
     * 画像抽取结果的结构化描述。
     */
    public record ProfileUpdate(
            String replyToStudent,
            String updatePath,
            Object updateValue,
            Double confidenceDelta,
            boolean dialogueDone,
            String nextIntent
    ) {}
}