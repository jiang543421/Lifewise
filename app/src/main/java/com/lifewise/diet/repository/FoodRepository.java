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
              and (cast(:q as string) is null
                   or lower(f.name) like lower(concat('%', cast(:q as string), '%')))
            """)
    Page<Food> searchByNameOrOwner(@Param("userId") Long userId,
                                   @Param("q") String q,
                                   Pageable pageable);

    /**
     * 全文匹配：name LIKE %q% OR aliases @> [q]（PG jsonb contains）。
     * 同时返回系统食物（user_id=NULL）与用户自定义。
     *
     * <p>{@code CAST(:q AS text) IS NOT NULL} 守卫：
     * native query 在 q=null 时不会被 PG 协议层拒绝，但
     * {@code to_jsonb(ARRAY[NULL]) @> aliases} 会把 JSON null
     * 元素宽松匹配所有 aliases（PG 14+ behavior），导致静默返回全表。
     * service 层已 guard，但 repository 是公共 API，显式 null 短路
     * 锁住"null q → 空结果"语义。
     */
    @Query(value = """
            SELECT * FROM foods
            WHERE deleted_at IS NULL
              AND (user_id IS NULL OR user_id = :userId)
              AND (CAST(:q AS text) IS NOT NULL
                   AND (lower(name) LIKE lower(concat('%', :q, '%'))
                        OR aliases @> to_jsonb(ARRAY[:q])))
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