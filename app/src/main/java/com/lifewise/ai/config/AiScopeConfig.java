package com.lifewise.ai.config;

import com.lifewise.ai.domain.enums.AiJobType;
import com.lifewise.ai.service.ollama.OllamaProperties;
import com.lifewise.ai.service.scope.AiDataScope;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 模块 Spring wiring（plan-06-ai §2.3 / §7.3 / §7.5；BR-19/22）。
 *
 * <p>集中注册两个 v1.0 暴露未启用的 bean：
 * <ol>
 *   <li>{@link #aiDataScopes()} — {@code Map<AiJobType, List<AiDataScope>>}，
 *       由 {@code ScopedDataDefinitions}（{@code @Component}，单构造器）
 *       在 Spring 5+ 下自动注入；后者被 {@code ScopedDataFetcher} 消费做白名单校验。</li>
 *   <li>{@link #ollamaProperties()} — {@link OllamaProperties}，
 *       {@code @ConfigurationProperties} 注解但仓库零 {@code @EnableConfigurationProperties} /
 *       {@code @ConfigurationPropertiesScan}，所以走显式工厂方法。默认值已够 —
 *       当前 {@code application.yml} 没有 {@code lifewise.ai.ollama.*} 段，
 *       显式 instantiate 等价于"全部默认值"。</li>
 * </ol>
 *
 * <p><b>v1.0 数据范围表</b>（与 {@code AiJobProcessor.SCOPES} 静态 map 保持一致，
 * 但这里走的是 Spring 容器 — 单元测试可直接 {@code new ScopedDataDefinitions(map)}）：
 * <ul>
 *   <li>DAILY_SUMMARY / WEEKLY_SUMMARY / HABIT_ANALYSIS — {@code tasks} (id/title/status/occurred_at)</li>
 *   <li>PLAN_REVIEW — {@code plans} (id/title/status/last_activity_at)</li>
 *   <li>MEAL_ANALYSIS — {@code meals} (id/type/occurred_at)</li>
 *   <li>EXPENSE_ANALYSIS — {@code expenses} (id/amount/currency/category/occurred_at)</li>
 *   <li>CUSTOM_PROMPT — 空（v1.0 不预定义，由用户 prompt 自行决定）</li>
 * </ul>
 *
 * <p><b>Future plan</b>: v1.1+ 引入 {@code ai-data-scopes.yml} + 显式
 * {@code @ConfigurationProperties} 类，替换此处硬编码；同时切换
 * {@link #ollamaProperties()} 为 {@code @EnableConfigurationProperties} 全局绑定。
 * 当前 v1.0 不引入 yml — 仓库全局无 {@code @ConfigurationPropertiesScan} /
 * {@code @EnableConfigurationProperties}，yml binding 不会被启用，引入 yml
 * 反而要改 {@code LifewiseApplication}，越界。
 */
@Configuration
public class AiScopeConfig {

    /**
     * v1.0 hardcoded scope registry. Replaced by yml binding in v1.1+.
     */
    @Bean
    public Map<AiJobType, List<AiDataScope>> aiDataScopes() {
        return Map.of(
                AiJobType.DAILY_SUMMARY, List.of(
                        new AiDataScope("tasks",
                                Set.of("id", "title", "status", "occurred_at"),
                                "occurred_at", "user_id")),
                AiJobType.WEEKLY_SUMMARY, List.of(
                        new AiDataScope("tasks",
                                Set.of("id", "title", "status", "occurred_at"),
                                "occurred_at", "user_id")),
                AiJobType.PLAN_REVIEW, List.of(
                        new AiDataScope("plans",
                                Set.of("id", "title", "status", "last_activity_at"),
                                "last_activity_at", "user_id")),
                AiJobType.HABIT_ANALYSIS, List.of(
                        new AiDataScope("tasks",
                                Set.of("id", "title", "status", "occurred_at"),
                                "occurred_at", "user_id")),
                AiJobType.MEAL_ANALYSIS, List.of(
                        new AiDataScope("meals",
                                Set.of("id", "type", "occurred_at"),
                                "occurred_at", "user_id")),
                AiJobType.EXPENSE_ANALYSIS, List.of(
                        new AiDataScope("expenses",
                                Set.of("id", "amount", "currency", "category", "occurred_at"),
                                "occurred_at", "user_id")),
                AiJobType.CUSTOM_PROMPT, List.of());
    }

    /**
     * Ollama 配置 bean（plan-06-ai §2.3）。v1.0 走默认值；v1.1+ 切换到
     * {@code @EnableConfigurationProperties(OllamaProperties.class)} 全局绑定。
     */
    @Bean
    public OllamaProperties ollamaProperties() {
        return new OllamaProperties();
    }
}
