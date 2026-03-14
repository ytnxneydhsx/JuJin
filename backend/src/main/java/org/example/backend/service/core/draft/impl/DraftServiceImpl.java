package org.example.backend.service.core.draft.impl;

import lombok.RequiredArgsConstructor;
import org.example.backend.common.constant.AppConstants.ArticleStatus;
import org.example.backend.common.constant.AppConstants.DraftStatus;
import org.example.backend.common.page.PageUtils;
import org.example.backend.event.article.ArticleChangedEvent;
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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DraftServiceImpl implements DraftService {

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
        entity.setStatus(DraftStatus.DRAFT);
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
                DraftStatus.DRAFT,
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
        if (draft == null || draft.getStatus() == null || draft.getStatus() != DraftStatus.DRAFT) {
            throw new BizException("DRAFT_NOT_FOUND", "Draft not found");
        }
        return toDraftVO(draft);
    }

    @Override
    public Page<ArticleDraftVO> listDrafts(Long userId, int page, int size) {
        validateUserId(userId);
        Pageable pageable = PageUtils.pageable(page, size);
        int offset = PageUtils.offset(pageable);

        List<ArticleDraftVO> records = articleDraftMapper.selectPageByUserId(
                        userId,
                        DraftStatus.DRAFT,
                        offset,
                        pageable.getPageSize()
                )
                .stream()
                .map(this::toDraftVO)
                .toList();
        long total = articleDraftMapper.countByUserId(userId, DraftStatus.DRAFT);
        return PageUtils.page(records, pageable, total);
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
        if (draft.getStatus() == null || draft.getStatus() == DraftStatus.DELETED) {
            throw new BizException("DRAFT_NOT_FOUND", "Draft not found");
        }
        if (draft.getStatus() == DraftStatus.PUBLISHED) {
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
            entity.setStatus(ArticleStatus.PUBLISHED);
            int inserted = articleMapper.insert(entity);
            if (inserted != 1 || entity.getId() == null) {
                throw new BizException("ARTICLE_PUBLISH_FAILED", "Failed to publish article");
            }
            articleId = entity.getId();
            articleMapper.upsertContentByArticleId(articleId, content);
            articleMapper.ensureStatsByArticleId(articleId, 0L, 0L, 0L);
        } else {
            int updated = articleMapper.updateByIdAndUserId(
                    articleId,
                    userId,
                    title,
                    trimToNull(draft.getSummary()),
                    trimToNull(draft.getCoverUrl()),
                    ArticleStatus.PUBLISHED
            );
            if (updated != 1) {
                throw new BizException("ARTICLE_NOT_FOUND", "Related article not found");
            }
            articleMapper.upsertContentByArticleId(articleId, content);
            articleMapper.ensureStatsByArticleId(articleId, 0L, 0L, 0L);
        }

        int draftUpdated = articleDraftMapper.markPublishedByIdAndUserId(
                draftId,
                userId,
                articleId,
                DraftStatus.PUBLISHED,
                DraftStatus.DRAFT
        );
        if (draftUpdated != 1) {
            throw new BizException("DRAFT_STATE_CONFLICT", "Draft status changed unexpectedly");
        }
        publishArticleChangedEvent(articleId);
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
                DraftStatus.DELETED,
                DraftStatus.DRAFT
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

    private void publishArticleChangedEvent(Long articleId) {
        eventPublisher.publishEvent(new ArticleChangedEvent(articleId));
    }
}
