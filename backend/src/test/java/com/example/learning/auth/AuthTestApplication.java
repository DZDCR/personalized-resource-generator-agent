package com.example.learning.auth;

import com.example.learning.common.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 仅装载 auth 模块的测试引导类，避免测试依赖 PostgreSQL/Redis/Neo4j/RabbitMQ。
 */
@SpringBootApplication(
        scanBasePackages = "com.example.learning.auth",
        excludeName = {
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
                "org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration",
                "org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration",
                "org.springframework.boot.autoconfigure.data.neo4j.Neo4jRepositoriesAutoConfiguration",
                "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
        })
@EnableJpaRepositories(basePackages = "com.example.learning.auth.repository")
@EntityScan(basePackages = "com.example.learning.auth.entity")
@Import(GlobalExceptionHandler.class)
public class AuthTestApplication {
}
