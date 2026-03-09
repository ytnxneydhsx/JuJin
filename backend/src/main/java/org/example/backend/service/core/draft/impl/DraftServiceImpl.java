package org.example.backend.service.core.draft.impl;

import lombok.RequiredArgsConstructor;
import org.example.backend.event.article.ArticleSearchSyncEvent;
import org.example.backend.exception.BizException;
import org.example.backend.mapper.article.ArticleDraftMapper;
import org.example.backend.mapper.article.ArticleMapper;
import org.example.backend.model.dto.article.SaveDraftDTO;
import org.example.backend.model.entity.ArticleDraftEntity;
import org.example.backend.model.entity.ArticleEntity;
import org.example.backend.model.vo.ArticleDraftVO;
import org.example.backend.service.core.draft.DraftService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DraftServiceImpl implements DraftService {

    private static final int ARTICLE_STATUS_PUBLISHED = 1;
    private static final int DRAFT_STATUS_DRAFT = 1;
    private static final int DRAFT_STATUS_PUBLISHED = 2;
    private static final int DRAFT_STATUS_DELETED = 3;

    private final ArticleMapper articleMapper;
    private final ArticleDraftMapper articleDraftMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public Long createDraft(Long userId, SaveDraftDTO dto) {
        validateUserId(userId);
        ArticleDraftEntity entity = new ArticleDraftEntity();
        entity.setUserId(userId);
        entity.setArticleId(dto.getArticleId());
        entity.setStatus(DRAFT_STATUS_DRAFT);
        entity.setTitle(trimToNull(dto.getTitle()));
        entity.setSummary(trimToNull(dto.getSummary()));
        entity.setCoverUrl(trimToNull(dto.getCoverUrl()));
        entity.setContent(dto.getContent());

        int affected = articleDraftMapper.insert(entity);
        if (affected != 1 || entity.getId() == null) {
            throw new BizException("DRAFT_CREATE_FAILED", "Failed to create draft");
        }
        return entity.getId();
    }

    @Override
    @Transactional
    public void updateDraft(Long userId, Long draftId, SaveDraftDTO dto) {
        validateUserId(userId);
        validatePositive("draftId", draftId);
        int affected = articleDraftMapper.updateByIdAndUserId(
                draftId,
                userId,
                dto.getArticleId(),
                DRAFT_STATUS_DRAFT,
                trimToNull(dto.getTitle()),
                trimToNull(dto.getSummary()),
                trimToNull(dto.getCoverUrl()),
                dto.getContent()
        );
        if (affected != 1) {
            throw new BizException("DRAFT_NOT_FOUND", "Draft not found");
        }
    }

    @Override
    public ArticleDraftVO getDraft(Long userId, Long draftId) {
        validateUserId(userId);
        validatePositive("draftId", draftId);
        ArticleDraftEntity draft = articleDraftMapper.selectByIdAndUserId(draftId, userId);
        if (draft == null || draft.getStatus() == null || draft.getStatus() != DRAFT_STATUS_DRAFT) {
            throw new BizException("DRAFT_NOT_FOUND", "Draft not found");
        }
        return toDraftVO(draft);
    }

    @Override
    public Page<ArticleDraftVO> listDrafts(Long userId, int page, int size) {
        validateUserId(userId);
        Pageable pageable = requirePageable(page, size);
        int offset = Math.toIntExact(pageable.getOffset());

        List<ArticleDraftVO> records = articleDraftMapper.selectPageByUserId(
                        userId,
                        DRAFT_STATUS_DRAFT,
                        offset,
                        pageable.getPageSize()
                )
                .stream()
                .map(this::toDraftVO)
                .toList();
        long total = articleDraftMapper.countByUserId(userId, DRAFT_STATUS_DRAFT);
        return new PageImpl<>(records, pageable, total);
    }

    @Override
    @Transactional
    public Long publishDraft(Long userId, Long draftId) {
        validateUserId(userId);
        validatePositive("draftId", draftId);

        ArticleDraftEntity draft = articleDraftMapper.selectByIdAndUserId(draftId, userId);
        if (draft == null) {
            throw new BizException("DRAFT_NOT_FOUND", "Draft not found");
        }
        if (draft.getStatus() == null || draft.getStatus() == DRAFT_STATUS_DELETED) {
            throw new BizException("DRAFT_NOT_FOUND", "Draft not found");
        }
        if (draft.getStatus() == DRAFT_STATUS_PUBLISHED) {
            throw new BizException("DRAFT_ALREADY_PUBLISHED", "Draft is already published");
        }

        String title = trimToNull(draft.getTitle());
        String content = trimToNull(draft.getContent());
        if (!StringUtils.hasText(title) || !StringUtils.hasText(content)) {
            throw new BizException("INVALID_PARAM", "Draft title and content are required before publishing");
        }

        Long articleId = draft.getArticleId();
        if (articleId == null) {
            ArticleEntity entity = new ArticleEntity();
            entity.setUserId(userId);
            entity.setTitle(title);
            entity.setSummary(trimToNull(draft.getSummary()));
            entity.setCoverUrl(trimToNull(draft.getCoverUrl()));
            entity.setContent(content);
            entity.setStatus(ARTICLE_STATUS_PUBLISHED);
            int inserted = articleMapper.insert(entity);
            if (inserted != 1 || entity.getId() == null) {
                throw new BizException("ARTICLE_PUBLISH_FAILED", "Failed to publish article");
            }
            articleId = entity.getId();
        } else {
            int updated = articleMapper.updateByIdAndUserId(
                    articleId,
                    userId,
                    title,
                    trimToNull(draft.getSummary()),
                    trimToNull(draft.getCoverUrl()),
                    content,
                    ARTICLE_STATUS_PUBLISHED
            );
            if (updated != 1) {
                throw new BizException("ARTICLE_NOT_FOUND", "Related article not found");
            }
        }

        int draftUpdated = articleDraftMapper.markPublishedByIdAndUserId(
                draftId,
                userId,
                articleId,
                DRAFT_STATUS_PUBLISHED,
                DRAFT_STATUS_DRAFT
        );
        if (draftUpdated != 1) {
            throw new BizException("DRAFT_STATE_CONFLICT", "Draft status changed unexpectedly");
        }
        publishArticleSearchSyncEvent(articleId);
        return articleId;
    }

    @Override
    @Transactional
    public void deleteDraft(Long userId, Long draftId) {
        validateUserId(userId);
        validatePositive("draftId", draftId);
        int affected = articleDraftMapper.softDeleteByIdAndUserId(
                draftId,
                userId,
                DRAFT_STATUS_DELETED,
                DRAFT_STATUS_DRAFT
        );
        if (affected != 1) {
            throw new BizException("DRAFT_NOT_FOUND", "Draft not found");
        }
    }

    private void validateUserId(Long userId) {
        validatePositive("userId", userId);
    }

    private void validatePositive(String fieldName, Long value) {
        if (value == null || value <= 0) {
            throw new BizException("INVALID_PARAM", fieldName + " must be a positive number");
        }
    }

    private Pageable requirePageable(int page, int size) {
        if (page < 0 || size <= 0) {
            throw new BizException("INVALID_PARAM", "Invalid pagination parameters: page must be >= 0 and size must be > 0");
        }
        return PageRequest.of(page, size);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private ArticleDraftVO toDraftVO(ArticleDraftEntity entity) {
        return ArticleDraftVO.builder()
                .draftId(entity.getId())
                .articleId(entity.getArticleId())
                .title(entity.getTitle())
                .summary(entity.getSummary())
                .coverUrl(entity.getCoverUrl())
                .content(entity.getContent())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private void publishArticleSearchSyncEvent(Long articleId) {
        eventPublisher.publishEvent(new ArticleSearchSyncEvent(articleId));
    }
}
