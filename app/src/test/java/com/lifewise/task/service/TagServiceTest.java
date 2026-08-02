package com.lifewise.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.lifewise.task.domain.TaskTag;
import com.lifewise.task.repository.TaskTagLinkRepository;
import com.lifewise.task.repository.TaskTagRepository;
import com.lifewise.task.service.exception.DuplicateTagNameException;
import com.lifewise.task.service.exception.TagNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock TaskTagRepository tagRepository;
    @Mock TaskTagLinkRepository linkRepository;
    TagService service;

    @BeforeEach
    void setUp() {
        service = new TagService(tagRepository, linkRepository);
    }

    @Test
    void create_rejects_duplicate_name() {
        when(tagRepository.findByUserIdAndName(7L, "x")).thenReturn(Optional.of(TaskTag.create(7L, "x", null)));
        assertThatThrownBy(() -> service.create(7L, "x", null))
                .isInstanceOf(DuplicateTagNameException.class);
    }

    @Test
    void create_persists_tag() {
        when(tagRepository.findByUserIdAndName(7L, "x")).thenReturn(Optional.empty());
        when(tagRepository.save(any(TaskTag.class))).thenAnswer(inv -> {
            TaskTag t = inv.getArgument(0);
            t.setIdInternal(1L);
            return t;
        });
        var view = service.create(7L, "x", "#ff0000");
        assertThat(view.id()).isEqualTo(1L);
        assertThat(view.name()).isEqualTo("x");
        assertThat(view.color()).isEqualTo("#ff0000");
    }

    @Test
    void rename_throws_when_not_found() {
        when(tagRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.rename(7L, 1L, "x", null))
                .isInstanceOf(TagNotFoundException.class);
    }

    @Test
    void rename_rejects_duplicate_name() {
        TaskTag existing = TaskTag.create(7L, "old", null);
        existing.setIdInternal(1L);
        TaskTag conflict = TaskTag.create(7L, "new", null);
        conflict.setIdInternal(2L);
        when(tagRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(tagRepository.findByUserIdAndName(7L, "new")).thenReturn(Optional.of(conflict));
        assertThatThrownBy(() -> service.rename(7L, 1L, "new", null))
                .isInstanceOf(DuplicateTagNameException.class);
    }

    @Test
    void list_returns_views() {
        when(tagRepository.findByUserIdOrderByNameAsc(7L)).thenReturn(List.of());
        assertThat(service.list(7L)).isEmpty();
    }

    @Test
    void soft_delete_throws_when_not_owned() {
        TaskTag tag = TaskTag.create(99L, "x", null);
        tag.setIdInternal(1L);
        when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
        assertThatThrownBy(() -> service.softDelete(7L, 1L))
                .isInstanceOf(TagNotFoundException.class);
    }
}