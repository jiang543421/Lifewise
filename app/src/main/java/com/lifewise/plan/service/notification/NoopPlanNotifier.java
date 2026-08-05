package com.lifewise.plan.service.notification;

import com.lifewise.plan.domain.Plan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** v1.0 默认实现：仅日志，不推送。 */
@Component
public class NoopPlanNotifier implements PlanNotifier {

    private static final Logger LOG = LoggerFactory.getLogger(NoopPlanNotifier.class);

    @Override
    public void notifyStale(Plan plan) {
        LOG.info("[plan] stale notify (noop) planId={} userId={} lastActivityAt={}",
                plan.getId(), plan.getUserId(), plan.getLastActivityAt());
    }
}