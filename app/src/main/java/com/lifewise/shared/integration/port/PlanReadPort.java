package com.lifewise.shared.integration.port;

import com.lifewise.shared.integration.port.snapshot.PlanSnapshot;
import java.util.List;
import java.util.Optional;

/**
 * Plan 模块对外只读端口（plan-shared-integration §2.2）。
 *
 * <p>实现由 plan 模块在 {@code com.lifewise.plan.port.out.PlanReadPortAdapter} 提供。
 */
public interface PlanReadPort {

    Optional<PlanSnapshot> findById(Long userId, Long planId);

    List<PlanSnapshot> findActiveByUser(Long userId);
}
