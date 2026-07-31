package com.lifewise.shared.integration.port;

import com.lifewise.shared.integration.port.snapshot.DailySnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Daily 模块对外只读端口（plan-shared-integration §2.2）。
 *
 * <p>实现由 daily 模块在 {@code com.lifewise.daily.port.out.DailyReadPortAdapter} 提供。
 */
public interface DailyReadPort {

    Optional<DailySnapshot> findByDate(Long userId, LocalDate date);

    List<DailySnapshot> findInRange(Long userId, LocalDate from, LocalDate to);
}
