package com.example.learning.agent;

import com.example.learning.agent.product.CourseDoc;
import com.example.learning.agent.product.ResourceBlueprint;
import com.example.learning.config.PromptLoader;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 文档智能体：基于蓝图生成课程讲解文档（Markdown）。
 */
@Component
public class DocWriterAgent extends BaseAgent {

    public DocWriterAgent(ChatClient.Builder chatClientBuilder, PromptLoader promptLoader) {
        super(chatClientBuilder, promptLoader);
    }

    @Override
    public String name() {
        return "doc_writer";
    }

    public Optional<CourseDoc> generate(ResourceBlueprint spec) {
        String tpl = template("doc_writer");
        String prompt = tpl
                .replace("{{topic}}", spec.topic())
                .replace("{{student_level}}", String.valueOf(spec.studentLevel()))
                .replace("{{knowledge_points}}", String.valueOf(spec.knowledgePoints()));
        return callEntity(prompt, CourseDoc.class);
    }
}