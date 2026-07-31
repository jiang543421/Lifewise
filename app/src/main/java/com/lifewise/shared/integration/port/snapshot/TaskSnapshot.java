package com.lifewise.shared.integration.port.snapshot;

import java.time.OffsetDateTime;

/**
 * 任务只读快照（plan-shared-integration §2.2）。
 *
 * <p>字段对齐 PG {@code task} 表（V1）+ 部分 {@code task_event}（V10）子集：
 * <ul>
 *   <li>{@code id} — task.id</li>
 *   <li>{@code userId} — task.user_id（所有权校验后由 port 注入）</li>
 *   <li>{@code title} — task.title</li>
 *   <li>{@code status} — task.status（OPEN / DONE / ARCHIVED）</li>
 *   <li>{@code createdAt} — task.created_at</li>
 *   <li>{@code completedAt} — task.completed_at（可为 null）</li>
 * </ul>
 *
 * <p>record 不可变；消费者若需转换（→ JSON / 域对象），在调用方做映射。
 */
public record TaskSnapshot(
        Long id,
        Long userId,
        String title,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt) {
}
