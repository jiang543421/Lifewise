package com.lifewise.diet.service;

import com.lifewise.diet.domain.Food;
import com.lifewise.diet.repository.FoodRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 预置系统食物库（plan-04-diet §3.1）。
 *
 * <p>应用启动时检查 foods 表，若系统食物（user_id=NULL）数量为 0 则种入常见食物。
 * id 冲突或重复种入时幂等：基于 name 查询，已存在则跳过。
 *
 * <p>在共享 {@code @SpringBootTest} 场景下，H2 测试 schema 尚未 Flyway 迁移，
 * 缺少 foods 表会导致 {@link PostConstruct} 抛 SQL 异常并阻断整个 ApplicationContext
 * 加载。改为 swallow + WARN 降级，与 Spring Boot DataInitializer 的容错语义一致。
 */
@Service
public class FoodSeedService {

    private static final Logger log = LoggerFactory.getLogger(FoodSeedService.class);

    private final FoodRepository repository;

    public FoodSeedService(FoodRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void seedIfEmpty() {
        try {
            seedIfEmptyInner();
        } catch (DataAccessException ex) {
            log.warn("FoodSeedService skipped: foods table unavailable ({}). "
                    + "This is expected in shared H2 IT contexts.",
                    ex.getMostSpecificCause().getMessage());
        }
    }

    @Transactional
    void seedIfEmptyInner() {
        List<Food> existing = repository.findAllSystem();
        if (!existing.isEmpty()) {
            log.info("FoodSeedService: skipped (existing system foods = {})", existing.size());
            return;
        }
        for (Food f : defaultFoods()) {
            repository.save(f);
        }
        log.info("FoodSeedService: seeded {} system foods", defaultFoods().size());
    }

    /** 5 个常见食物用于冒烟测试 + v1.0 真实场景。 */
    private List<Food> defaultFoods() {
        // system(name, category, kcal, protein, carb, fat) — 注意参数顺序：kcal/protein/carb/fat
        return List.of(
                Food.system("Apple", "fruit", 52d, 0.3d, 14d, 0.2d),
                Food.system("Banana", "fruit", 89d, 1.1d, 23d, 0.3d),
                Food.system("Chicken breast (cooked)", "meat", 165d, 31d, 0d, 3.6d),
                Food.system("Brown rice (cooked)", "grain", 123d, 2.7d, 26d, 0.97d),
                Food.system("Egg (boiled)", "protein", 155d, 13d, 1.1d, 11d)
        );
    }
}