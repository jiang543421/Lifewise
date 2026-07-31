package com.lifewise.shared.integration.port;

import com.lifewise.shared.integration.port.snapshot.ExpenseSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Expense 模块对外只读端口（plan-shared-integration §2.2）。
 *
 * <p>实现由 expense 模块在 {@code com.lifewise.expense.port.out.ExpenseReadPortAdapter} 提供。
 */
public interface ExpenseReadPort {

    Optional<ExpenseSnapshot> findById(Long userId, Long expenseId);

    List<ExpenseSnapshot> findInRange(Long userId, LocalDate from, LocalDate to);
}
