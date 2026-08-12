package com.lifewise.task.service;

import com.lifewise.task.domain.TaskTag;
import com.lifewise.task.dto.TaskTagView;
import com.lifewise.task.repository.TaskTagLinkRepository;
import com.lifewise.task.repository.TaskTagRepository;
import com.lifewise.task.service.exception.DuplicateTagNameException;
import com.lifewise.task.service.exception.TagNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** TaskTag 增删改查（plan-01-task §2.3）。 */
@Service
public class TagService {

    private final TaskTagRepository tagRepository;
    private final TaskTagLinkRepository linkRepository;

    public TagService(TaskTagRepository tagRepository, TaskTagLinkRepository linkRepository) {
        this.tagRepository = tagRepository;
        this.linkRepository = linkRepository;
    }

    @Transactional(readOnly = true)
    public List<TaskTagView> list(long userId) {
        return tagRepository.findByUserIdOrderByNameAsc(userId)
                .stream().map(TaskTagView::from).toList();
    }

    @Transactional
    public TaskTagView create(long userId, String name, String color) {
        if (tagRepository.findByUserIdAndName(userId, name).isPresent()) {
            throw new DuplicateTagNameException(name);
        }
        TaskTag tag = TaskTag.create(userId, name, color);
        return TaskTagView.from(tagRepository.save(tag));
    }

    @Transactional
    public TaskTagView rename(long userId, long tagId, String name, String color) {
        TaskTag tag = tagRepository.findById(tagId)
                .filter(t -> userId == t.getUserId() && !t.isDeleted())
                .orElseThrow(() -> new TagNotFoundException(tagId));
        if (name != null && !name.isBlank() && !name.equals(tag.getName())
                && tagRepository.findByUserIdAndName(userId, name).isPresent()) {
            throw new DuplicateTagNameException(name);
        }
        tag.rename(name, color);
        return TaskTagView.from(tagRepository.save(tag));
    }

    @Transactional
    public void softDelete(long userId, long tagId) {
        TaskTag tag = tagRepository.findById(tagId)
                .filter(t -> userId == t.getUserId() && !t.isDeleted())
                .orElseThrow(() -> new TagNotFoundException(tagId));
        // 修复（H2）：detach 所有 task_tag_links 后再删除 tag，避免孤儿 link 行
        linkRepository.deleteByIdTaskId(tagId);
        tagRepository.delete(tag);
    }
}
