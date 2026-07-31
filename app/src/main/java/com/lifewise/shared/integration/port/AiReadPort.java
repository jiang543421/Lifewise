package com.lifewise.shared.integration.port;

import com.lifewise.shared.integration.port.snapshot.AiSnapshot;
import java.util.List;
import java.util.Optional;

/**
 * AI 模块对外只读端口（plan-shared-integration §2.2）。
 *
 * <p>实现由 ai 模块在 {@code com.lifewise.ai.port.out.AiReadPortAdapter} 提供。
 */
public interface AiReadPort {

    Optional<AiSnapshot> findLatestReport(Long userId);

    List<AiSnapshot> findRecentReports(Long userId, int limit);
}
