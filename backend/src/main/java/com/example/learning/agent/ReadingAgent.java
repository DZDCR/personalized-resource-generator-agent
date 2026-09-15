package com.example.learning.agent;

import com.example.learning.agent.product.ReadingMaterial;
import com.example.learning.agent.product.ResourceBlueprint;
import com.example.learning.config.PromptLoader;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 拓展阅读智能体：围绕主题生成延伸阅读材料。
 */
@Component
public class ReadingAgent extends BaseAgent {

    public ReadingAgent(ChatClient.Builder chatClientBuilder, PromptLoader promptLoader) {
        super(chatClientBuilder, promptLoader);
    }

    @Override
    public String name() {
        return "reading";
    }

    public Optional<ReadingMaterial> generate(ResourceBlueprint spec) {
        String tpl = template("reading");
        String prompt = tpl.replace("{{topic}}", spec.topic());
        return callEntity(prompt, ReadingMaterial.class);
    }
}