package com.lifewise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Lifewise 启动入口。
 *
 * <p>v1.0 path B：开启 {@link EnableScheduling @EnableScheduling}，由
 * {@code OutboxWorker.scheduledRun()} 通过 {@code @Scheduled} 拉取 outbox 批次；
 * 测试可通过 {@code outbox.scheduler.enabled=false} 关闭调度（避免 IT 时多线程竞争）。
 */
@SpringBootApplication
@EnableScheduling
public class LifewiseApplication {

    public static void main(String[] args) {
        SpringApplication.run(LifewiseApplication.class, args);
    }
}
