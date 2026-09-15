package com.example.learning.agent;

import com.example.learning.agent.product.CodeCaseSet;
import com.example.learning.agent.product.ResourceBlueprint;
import com.example.learning.config.PromptLoader;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 代码实操智能体：生成可运行代码案例 + 运行示例 + 变体练习。
 */
@Component
public class CoderAgent extends BaseAgent {

    public CoderAgent(ChatClient.Builder chatClientBuilder, PromptLoader promptLoader) {
        super(chatClientBuilder, promptLoader);
    }

    @Override
    public String name() {
        return "coder";
    }

    public Optional<CodeCaseSet> generate(ResourceBlueprint spec) {
        String tpl = template("coder");
        String prompt = tpl
                .replace("{{topic}}", spec.topic())
                .replace("{{difficulty}}", spec.difficulty());
        return callEntity(prompt, CodeCaseSet.class);
    }
}