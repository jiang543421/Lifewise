package com.lifewise.plan.domain;

/**
 * Plan 状态机（V4 plans.status CHECK 约束）。
 *
 * <p>对齐 PG {@code plans.status IN ('ACTIVE','COMPLETED','ARCHIVED','CANCELLED')}
 * （plan-data-flyway V4）。规范文档里描述的 {@code DONE/ABANDONED} 命名
 * 在 V4 已统一改为 {@code COMPLETED/CANCELLED}。
 */
public enum PlanStatus {
    /** 默认状态；可读写、可添加里程碑。 */
    ACTIVE,

    /** 所有里程碑 DONE 后由系统或用户标记。 */
    COMPLETED,

    /** 长期冷归档（隐藏但保留）；v1.0 不暴露编辑入口。 */
    ARCHIVED,

    /** 用户主动放弃或软删等同于 CANCELLED。 */
    CANCELLED
}