package com.example.learning.agent;

import com.example.learning.agent.product.QuizSet;
import com.example.learning.agent.product.ResourceBlueprint;
import com.example.learning.config.PromptLoader;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 题库智能体：按难度/题型生成练习题目（含答案与解析）。
 */
@Component
public class QuizAgent extends BaseAgent {

    public QuizAgent(ChatClient.Builder chatClientBuilder, PromptLoader promptLoader) {
        super(chatClientBuilder, promptLoader);
    }

    @Override
    public String name() {
        return "quiz";
    }

    public Optional<QuizSet> generate(ResourceBlueprint spec) {
        String tpl = template("quiz");
        String prompt = tpl
                .replace("{{topic}}", spec.topic())
                .replace("{{knowledge_points}}", String.valueOf(spec.knowledgePoints()))
                .replace("{{difficulty}}", spec.difficulty());
        return callEntity(prompt, QuizSet.class);
    }
}