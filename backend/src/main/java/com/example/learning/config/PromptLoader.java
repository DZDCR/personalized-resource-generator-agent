package com.example.learning.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt 模板加载器：从 classpath:/prompt/*.md 读取，支持带缓存的运行时热更新。
 */
@Slf4j
@Component
public class PromptLoader {

    private static final String BASE = "classpath*:/prompt/*.md";
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    private final PathMatchingResourcePatternResolver resolver =
            new PathMatchingResourcePatternResolver();

    /** 按文件名（不含扩展名）加载模板，如 "doc_writer" */
    public String load(String name) {
        return cache.computeIfAbsent(name, this::doLoad);
    }

    /** 清空缓存，实现运行时模板热更新 */
    public void invalidateAll() {
        cache.clear();
        log.info("prompt template cache cleared");
    }

    private String doLoad(String name) {
        try {
            Resource[] resources = resolver.getResources(BASE);
            for (Resource r : resources) {
                String filename = r.getFilename();
                if (filename != null && filename.endsWith(".md")
                        && filename.substring(0, filename.length() - 3).equals(name)) {
                    String content = r.getContentAsString(StandardCharsets.UTF_8);
                    log.info("loaded prompt template: {}", name);
                    return content;
                }
            }
            log.warn("prompt template not found: {}", name);
            return name;
        } catch (Exception e) {
            log.error("failed to load prompt template {}", name, e);
            return name;
        }
    }
}