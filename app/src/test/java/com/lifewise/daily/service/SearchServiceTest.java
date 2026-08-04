package com.lifewise.daily.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.daily.dto.DailyReportSearchHit;
import com.lifewise.daily.repository.DailyReportRepository;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock DailyReportRepository repository;
    SearchService service;

    @BeforeEach
    void setUp() {
        service = new SearchService(repository);
    }

    @Test
    void empty_query_returns_empty_page_without_querying_repo() {
        Page<DailyReportSearchHit> result = service.search(7L, "   ", null, null,
                PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        verify(repository, never()).fullTextSearch(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(Pageable.class));
    }

    @Test
    void search_maps_repository_results_to_hits() {
        Object[] row = new Object[] { 11L, Date.valueOf(LocalDate.of(2026, 8, 2)),
                "<em>snip</em>", 0.42 };
        Page<Object[]> repoPage = new PageImpl<>(List.<Object[]>of(row));
        when(repository.fullTextSearch(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq("hello"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(repoPage);

        Page<DailyReportSearchHit> hits = service.search(7L, "hello", null, null,
                PageRequest.of(0, 20));

        assertThat(hits.getContent()).hasSize(1);
        assertThat(hits.getContent().get(0).reportId()).isEqualTo(11L);
        assertThat(hits.getContent().get(0).reportDate()).isEqualTo(LocalDate.of(2026, 8, 2));
        assertThat(hits.getContent().get(0).score()).isEqualTo(0.42);
    }

    @Test
    void search_handles_null_score_gracefully() {
        Object[] row = new Object[] { 12L, Date.valueOf(LocalDate.of(2026, 8, 3)), "snip", null };
        Page<Object[]> repoPage = new PageImpl<>(List.<Object[]>of(row));
        when(repository.fullTextSearch(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(repoPage);

        Page<DailyReportSearchHit> hits = service.search(7L, "x", null, null,
                PageRequest.of(0, 20));

        assertThat(hits.getContent().get(0).score()).isZero();
    }
}