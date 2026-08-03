package com.lifewise.diet.repository;

import com.lifewise.diet.domain.Food;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoodRepository extends JpaRepository<Food, Long> {

    Optional<Food> findByIdAndDeletedAtIsNull(Long id);

    @Query("""
            select f from Food f
            where f.deletedAt is null
              and (f.userId is null or f.userId = :userId)
              and (:q is null or lower(f.name) like lower(concat('%', :q, '%')))
            """)
    Page<Food> searchByNameOrOwner(@Param("userId") Long userId,
                                   @Param("q") String q,
                                   Pageable pageable);

    /**
     * 全文匹配：name LIKE %q% OR aliases @> [q]（PG jsonb contains）。
     * 同时返回系统食物（user_id=NULL）与用户自定义。
     */
    @Query(value = """
            SELECT * FROM foods
            WHERE deleted_at IS NULL
              AND (user_id IS NULL OR user_id = :userId)
              AND (lower(name) LIKE lower(concat('%', :q, '%'))
                   OR aliases @> to_jsonb(ARRAY[:q]))
            ORDER BY (user_id IS NULL) DESC, name ASC
            LIMIT 50
            """, nativeQuery = true)
    List<Food> searchByNameOrAlias(@Param("userId") Long userId,
                                   @Param("q") String q);

    @Query("""
            select f from Food f
            where f.deletedAt is null
              and f.userId is null
            order by f.name asc
            """)
    List<Food> findAllSystem();
}