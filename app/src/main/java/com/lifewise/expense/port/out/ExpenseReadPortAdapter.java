package com.lifewise.expense.port.out;

import com.lifewise.expense.domain.Expense;
import com.lifewise.expense.repository.ExpenseRepository;
import com.lifewise.shared.integration.port.ExpenseReadPort;
import com.lifewise.shared.integration.port.snapshot.ExpenseSnapshot;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Expense 模块对其他 5 个模块的只读端口实现（plan-03-expense §3.5）。
 *
 * <p>所有方法自动过滤 {@code deletedAt IS NULL}；cents 长整型；按 {@code user_id} 隔离。
 */
@Component
public class ExpenseReadPortAdapter implements ExpenseReadPort {

    private final ExpenseRepository expenseRepository;

    public ExpenseReadPortAdapter(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    @Override
    public Optional<ExpenseSnapshot> findById(Long userId, Long expenseId) {
        return expenseRepository.findByIdAndUserIdAndDeletedAtIsNull(expenseId, userId)
                .map(this::toSnapshot);
    }

    @Override
    public List<ExpenseSnapshot> findInRange(Long userId, LocalDate from, LocalDate to) {
        return expenseRepository
                .findByUserIdAndLocalDateBetweenAndDeletedAtIsNullOrderByOccurredAtDesc(userId, from, to)
                .stream()
                .map(this::toSnapshot)
                .toList();
    }

    @Override
    public List<ExpenseSnapshot> findByCategory(Long userId, Long categoryId, int limit) {
        return expenseRepository
                .findByUserIdAndCategoryIdAndDeletedAtIsNullOrderByOccurredAtDesc(
                        userId, categoryId, PageRequest.of(0, limit))
                .stream()
                .map(this::toSnapshot)
                .toList();
    }

    @Override
    public long sumInRange(Long userId, LocalDate from, LocalDate to) {
        return expenseRepository.sumInRangeCents(userId, from, to);
    }

    @Override
    public Map<Long, Long> sumByCategoryInRange(Long userId, LocalDate from, LocalDate to) {
        List<Expense> rows = expenseRepository
                .findByUserIdAndLocalDateBetweenAndDeletedAtIsNullOrderByOccurredAtDesc(userId, from, to);
        Map<Long, Long> acc = new HashMap<>();
        for (Expense e : rows) {
            acc.merge(e.getCategoryId(), e.getAmountCents(), Long::sum);
        }
        return acc;
    }

    private ExpenseSnapshot toSnapshot(Expense e) {
        return new ExpenseSnapshot(
                e.getId(),
                e.getUserId(),
                e.getAmountCents(),
                e.getCurrency(),
                e.getCategoryId(),
                e.getLocalDate());
    }
}