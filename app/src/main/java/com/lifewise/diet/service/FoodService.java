package com.lifewise.diet.service;

import com.lifewise.diet.domain.Food;
import com.lifewise.diet.dto.FoodCreateRequest;
import com.lifewise.diet.dto.FoodView;
import com.lifewise.diet.repository.FoodRepository;
import com.lifewise.diet.service.exception.FoodNotFoundException;
import com.lifewise.diet.service.exception.FoodSystemReadOnlyException;
import com.lifewise.diet.service.exception.NegativeNutrientException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Food CRUD + 搜索（plan-04-diet §5.2）。 */
@Service
public class FoodService {

    private final FoodRepository repository;

    public FoodService(FoodRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public FoodView create(Long userId, FoodCreateRequest req) {
        validateNutrients(req);
        Food food = Food.create(userId, req.name(), req.aliases(), req.category(),
                req.kcalPer100g(), req.proteinGPer100g(),
                req.fatGPer100g(), req.carbGPer100g());
        food = repository.save(food);
        return FoodView.from(food);
    }

    @Transactional
    public FoodView update(Long userId, Long foodId, FoodCreateRequest req) {
        validateNutrients(req);
        Food food = loadOwned(userId, foodId);
        if (food.isSystem()) {
            throw new FoodSystemReadOnlyException(foodId);
        }
        food.applyUpdate(req.name(), req.aliases(), req.category(),
                req.kcalPer100g(), req.proteinGPer100g(),
                req.fatGPer100g(), req.carbGPer100g());
        food = repository.save(food);
        return FoodView.from(food);
    }

    @Transactional
    public void delete(Long userId, Long foodId) {
        Food food = loadOwned(userId, foodId);
        if (food.isSystem()) {
            throw new FoodSystemReadOnlyException(foodId);
        }
        food.softDelete();
        repository.save(food);
    }

    @Transactional(readOnly = true)
    public FoodView get(Long userId, Long foodId) {
        return FoodView.from(loadOwned(userId, foodId));
    }

    @Transactional(readOnly = true)
    public List<FoodView> list(Long userId, String q, String category, int page, int limit) {
        int p = Math.max(page, 1) - 1;
        int l = Math.max(Math.min(limit, 100), 1);
        return repository.searchByNameOrOwner(userId, q, PageRequest.of(p, l))
                .map(FoodView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FoodView> search(Long userId, String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return repository.searchByNameOrAlias(userId, q.trim()).stream()
                .map(FoodView::from)
                .toList();
    }

    private Food loadOwned(Long userId, Long foodId) {
        Food food = repository.findByIdAndDeletedAtIsNull(foodId)
                .orElseThrow(() -> new FoodNotFoundException(foodId));
        // 系统食物对所有 user 可见（userId=null）；用户食物必须归属
        if (!food.isSystem() && !food.getUserId().equals(userId)) {
            throw new FoodNotFoundException(foodId);
        }
        return food;
    }

    private void validateNutrients(FoodCreateRequest req) {
        requireNonNegative(req.kcalPer100g(), "kcalPer100g");
        requireNonNegative(req.proteinGPer100g(), "proteinGPer100g");
        requireNonNegative(req.fatGPer100g(), "fatGPer100g");
        requireNonNegative(req.carbGPer100g(), "carbGPer100g");
    }

    private void requireNonNegative(BigDecimal v, String field) {
        if (v == null || v.signum() < 0) {
            throw new NegativeNutrientException(field);
        }
    }
}