package com.lifewise.shared.integration.port.snapshot;

/**
 * 时间区间内某分类的消费总额（cents）。用于 {@link com.lifewise.shared.integration.port.ExpenseReadPort#sumByCategoryInRange}。
 *
 * <p>为何不用 {@code Map<Long, Long>}：PortContractTest 强制只读端口集合返回仅限 List/Optional/标量。
 */
public record CategoryTotal(Long categoryId, long totalCents) {}
