package com.example.learning;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo")
class LearningApplicationTests {

    @Test
    void contextLoads() {
        // 无数据库依赖时仅验证 Spring 上下文装配
    }
}