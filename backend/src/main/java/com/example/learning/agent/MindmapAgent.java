package com.example.learning.agent;

import com.example.learning.agent.product.MindmapData;
import com.example.learning.agent.product.ResourceBlueprint;
import com.example.learning.config.PromptLoader;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 思维导图智能体：从知识点提炼层级结构，产出 markmap JSON 树。
 */
@Component
public class MindmapAgent extends BaseAgent {

    public MindmapAgent(ChatClient.Builder chatClientBuilder, PromptLoader promptLoader) {
        super(chatClientBuilder, promptLoader);
    }

    @Override
    public String name() {
        return "mindmap";
    }

    public Optional<MindmapData> generate(ResourceBlueprint spec) {
        String tpl = template("mindmap");
        String prompt = tpl
                .replace("{{topic}}", spec.topic())
                .replace("{{knowledge_points}}", String.valueOf(spec.knowledgePoints()));
        return callEntity(prompt, MindmapData.class);
    }
}