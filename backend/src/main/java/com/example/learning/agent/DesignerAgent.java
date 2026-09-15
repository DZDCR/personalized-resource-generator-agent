package com.example.learning.agent;

import com.example.learning.agent.product.ResourceBlueprint;
import com.example.learning.config.PromptLoader;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 设计智能体：解析用户需求与画像，产出资源生成蓝图。
 * 蓝图是所有后续 Agent 的统一输入。
 */
@Component
public class DesignerAgent extends BaseAgent {

    public DesignerAgent(ChatClient.Builder chatClientBuilder, PromptLoader promptLoader) {
        super(chatClientBuilder, promptLoader);
    }

    @Override
    public String name() {
        return "designer";
    }

    public Optional<ResourceBlueprint> parse(String topic, String profileJson) {
        String tpl = template("designer");
        String prompt = tpl
                .replace("{{topic}}", topic)
                .replace("{{profile}}", profileJson);
        return callEntity(prompt, ResourceBlueprint.class);
    }
}