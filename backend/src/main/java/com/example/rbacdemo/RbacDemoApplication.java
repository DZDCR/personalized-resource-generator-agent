package com.example.rbacdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import com.example.learning.common.GlobalExceptionHandler;

/**
 * RBAC3 独立演示入口。
 *
 * <p>只加载 auth 模块与 H2 数据源，便于集成测试或裁剪部署。
 * 常规演示使用主应用 + {@code rbac-demo} profile（见 README），
 * 正式系统仍从 {@code LearningApplication} 启动。</p>
 */
@SpringBootApplication(
        scanBasePackages = {"com.example.learning.auth"},
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
public class RbacDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(RbacDemoApplication.class, args);
    }
}
