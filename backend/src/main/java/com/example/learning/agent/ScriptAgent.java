package com.example.learning.agent;

import com.example.learning.agent.product.ResourceBlueprint;
import com.example.learning.agent.product.VideoScript;
import com.example.learning.config.PromptLoader;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 视频脚本智能体：基于讲解内容生成教学视频分镜脚本。
 */
@Component
public class ScriptAgent extends BaseAgent {

    public ScriptAgent(ChatClient.Builder chatClientBuilder, PromptLoader promptLoader) {
        super(chatClientBuilder, promptLoader);
    }

    @Override
    public String name() {
        return "script";
    }

    public Optional<VideoScript> generate(ResourceBlueprint spec, String docOutline) {
        String tpl = template("script");
        String prompt = tpl
                .replace("{{topic}}", spec.topic())
                .replace("{{doc_outline}}", docOutline);
        return callEntity(prompt, VideoScript.class);
    }
}