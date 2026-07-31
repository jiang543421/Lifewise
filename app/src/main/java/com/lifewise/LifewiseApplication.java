package com.lifewise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Lifewise 启动入口（MVP 1A 阶段：仅占位，无业务 Bean）
 *
 * <p>当前阶段（plan-data-flyway）只验证数据层 Flyway 迁移就位；业务模块按
 * plan-01-task.md ~ plan-06-ai.md 顺序追加，本类保持最薄壳即可。</p>
 */
@SpringBootApplication
public class LifewiseApplication {

    public static void main(String[] args) {
        SpringApplication.run(LifewiseApplication.class, args);
    }
}
