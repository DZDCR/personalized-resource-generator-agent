package com.example.learning.agent;

import com.example.learning.config.PromptLoader;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 辅导智能体：概念/计算/代码三类问题的多模态解答生成（加分项）。
 */
@Component
public class TutorAgent extends BaseAgent {

    public TutorAgent(ChatClient.Builder chatClientBuilder, PromptLoader promptLoader) {
        super(chatClientBuilder, promptLoader);
    }

    @Override
    public String name() {
        return "tutor";
    }

    public Optional<TutorAnswer> explain(String question, String profileJson, String questionType) {
        String tpl = template("tutor");
        String prompt = tpl
                .replace("{{question}}", question)
                .replace("{{question_type}}", questionType)
                .replace("{{profile}}", String.valueOf(profileJson));
        return callEntity(prompt, TutorAnswer.class);
    }

    /**
     * 辅导解答结果（文本 + 图解/代码差异结构）。
     */
    public record TutorAnswer(
            String text,
            String visualBlock,
            String socraticHint,
            java.util.List<String> relatedTopics
    ) {}
}