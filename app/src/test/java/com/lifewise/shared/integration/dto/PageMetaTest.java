package com.lifewise.shared.integration.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PageMeta 单测（plan-shared-integration §5.3）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>{@code dto_should_paginate_with_meta}</li>
 *   <li>{@code hasNext → has_next}（snake_case 序列化，与 API 信封约定一致）</li>
 * </ul>
 */
@DisplayName("PageMeta 分页元数据")
class PageMetaTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("record 字段为 total/page/limit/hasNext（驼峰）")
    void record_fields_use_camel_case() {
        PageMeta meta = new PageMeta(42L, 3, 10, false);

        assertThat(meta.total()).isEqualTo(42L);
        assertThat(meta.page()).isEqualTo(3);
        assertThat(meta.limit()).isEqualTo(10);
        assertThat(meta.hasNext()).isFalse();
    }

    @Test
    @DisplayName("JSON 序列化 hasNext → has_next（snake_case）")
    void serialized_json_uses_snake_case() throws Exception {
        PageMeta meta = new PageMeta(101L, 1, 20, true);
        String json = mapper.writeValueAsString(meta);

        JsonNode node = mapper.readTree(json);
        assertThat(node.get("total").asLong()).isEqualTo(101L);
        assertThat(node.get("page").asInt()).isEqualTo(1);
        assertThat(node.get("limit").asInt()).isEqualTo(20);
        assertThat(node.get("has_next").asBoolean()).isTrue();
        assertThat(node.has("hasNext"))
                .as("Java 字段 hasNext 不应在 JSON 出现")
                .isFalse();
    }

    @Test
    @DisplayName("hasNext=false 时 JSON 字段仍存在（与 ApiResponse.meta=null 区分）")
    void has_next_false_still_serialized() throws Exception {
        PageMeta meta = new PageMeta(0L, 1, 20, false);
        String json = mapper.writeValueAsString(meta);

        JsonNode node = mapper.readTree(json);
        assertThat(node.has("has_next")).isTrue();
        assertThat(node.get("has_next").asBoolean()).isFalse();
    }
}
