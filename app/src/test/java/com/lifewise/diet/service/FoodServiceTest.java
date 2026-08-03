package com.lifewise.diet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.diet.domain.Food;
import com.lifewise.diet.service.exception.FoodSystemReadOnlyException;
import com.lifewise.diet.service.exception.NegativeNutrientException;
import com.lifewise.diet.dto.FoodCreateRequest;
import com.lifewise.diet.dto.FoodView;
import com.lifewise.diet.repository.FoodRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * FoodService CRUD + 搜索 + 系统只读（plan-04-diet §5.2）。
 */
@ExtendWith(MockitoExtension.class)
class FoodServiceTest {

    @Mock FoodRepository repository;

    FoodService service;

    @BeforeEach
    void setUp() {
        service = new FoodService(repository);
    }

    @Test
    @DisplayName("create 普通食物（kcal=0）→ 成功")
    void create_zero_kcal_succeeds() {
        when(repository.save(any(Food.class))).thenAnswer(inv -> {
            Food f = inv.getArgument(0);
            f.setIdInternal(11L);
            return f;
        });
        FoodCreateRequest req = new FoodCreateRequest("芹菜", null, "VEGETABLE",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        FoodView view = service.create(1L, req);

        assertThat(view.id()).isEqualTo(11L);
        assertThat(view.name()).isEqualTo("芹菜");
        assertThat(view.kcalPer100g()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("create 负 kcal → NegativeNutrientException")
    void create_rejects_negative_kcal() {
        FoodCreateRequest req = new FoodCreateRequest("bad", null, "OTHER",
                new BigDecimal("-1.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThatThrownBy(() -> service.create(1L, req))
                .isInstanceOf(NegativeNutrientException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create 任一 macro < 0 → NegativeNutrientException")
    void create_rejects_negative_macro() {
        FoodCreateRequest req = new FoodCreateRequest("bad", null, "OTHER",
                new BigDecimal("100.00"), new BigDecimal("-1.00"), BigDecimal.ZERO, BigDecimal.ZERO);

        assertThatThrownBy(() -> service.create(1L, req))
                .isInstanceOf(NegativeNutrientException.class);
    }

    @Test
    @DisplayName("update 系统食物（user_id=NULL）→ FoodSystemReadOnlyException")
    void update_rejects_system_food() {
        Food systemFood = Food.system("白米饭", "STAPLE", 130, 2.7, 28.0, 0.3);
        systemFood.setIdInternal(99L);
        when(repository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.of(systemFood));

        FoodCreateRequest req = new FoodCreateRequest("改", null, "STAPLE",
                BigDecimal.valueOf(130), BigDecimal.valueOf(2.7),
                BigDecimal.valueOf(28), BigDecimal.valueOf(0.3));

        assertThatThrownBy(() -> service.update(1L, 99L, req))
                .isInstanceOf(FoodSystemReadOnlyException.class);
    }

    @Test
    @DisplayName("delete 系统食物 → FoodSystemReadOnlyException")
    void delete_rejects_system_food() {
        Food systemFood = Food.system("白米饭", "STAPLE", 130, 2.7, 28.0, 0.3);
        systemFood.setIdInternal(99L);
        when(repository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.of(systemFood));

        assertThatThrownBy(() -> service.delete(1L, 99L))
                .isInstanceOf(FoodSystemReadOnlyException.class);
    }

    @Test
    @DisplayName("delete 用户自定义食物 → 软删成功")
    void delete_user_food_soft_deletes() {
        Food userFood = Food.user(1L, "我的便当", "MEAL", 600, 30, 60, 20);
        userFood.setIdInternal(50L);
        when(repository.findByIdAndDeletedAtIsNull(50L)).thenReturn(Optional.of(userFood));
        when(repository.save(any(Food.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(1L, 50L);

        assertThat(userFood.getDeletedAt()).isNotNull();
        verify(repository).save(userFood);
    }

    @Test
    @DisplayName("search by name delegates to repository.searchByNameOrAlias(userId, q)")
    void search_matches_aliases() {
        Food tomato = Food.system("Tomato", "VEGETABLE", 18d, 0.9d, 3.9d, 0.2d);
        tomato.setIdInternal(1L);
        when(repository.searchByNameOrAlias(1L, "tomato")).thenReturn(List.of(tomato));

        List<FoodView> results = service.search(1L, "tomato");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).isEqualTo("Tomato");
        verify(repository).searchByNameOrAlias(1L, "tomato");
    }
}