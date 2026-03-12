package org.example.backend.service.core.article.impl;

import lombok.RequiredArgsConstructor;
import org.example.backend.event.article.ArticleSearchSyncEvent;
import org.example.backend.exception.BizException;
import org.example.backend.mapper.article.ArticleMapper;
import org.example.backend.model.dto.article.UpdateArticleDTO;
import org.example.backend.service.core.article.ArticleService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    private static final int STATUS_PUBLISHED = 1;
    private static final int STATUS_HIDDEN = 2;

    private final ArticleMapper articleMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void updateArticle(Long userId, Long articleId, UpdateArticleDTO dto) {
        validateUserId(userId);
        validatePositive("articleId", articleId);

        String title = trimToNull(dto.getTitle());
        String content = trimToNull(dto.getContent());
        if (!StringUtils.hasText(title) || !StringUtils.hasText(content)) {
            throw new BizException("INVALID_PARAM", "title and content cannot be blank");
        }

        Integer status = dto.getStatus();
        if (status != null && (status != STATUS_PUBLISHED && status != STATUS_HIDDEN)) {
            throw new BizException("INVALID_PARAM", "status must be 1 or 2");
        }

        int affected = articleMapper.updateByIdAndUserId(
                articleId,
                userId,
                title,
                trimToNull(dto.getSummary()),
                trimToNull(dto.getCoverUrl()),
                status
        );
        if (affected != 1) {
            throw new BizException("ARTICLE_NOT_FOUND", "Article not found");
        }
        articleMapper.upsertContentByArticleId(articleId, content);
        articleMapper.ensureStatsByArticleId(articleId, 0L, 0L, 0L);
        publishArticleSearchSyncEvent(articleId);
    }

    @Override
    @Transactional
    public void deleteArticle(Long userId, Long articleId) {
        validateUserId(userId);
        validatePositive("articleId", articleId);
        int affected = articleMapper.softDeleteByIdAndUserId(articleId, userId);
        if (affected != 1) {
            throw new BizException("ARTICLE_NOT_FOUND", "Article not found");
        }
        publishArticleSearchSyncEvent(articleId);
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

    private void publishArticleSearchSyncEvent(Long articleId) {
        eventPublisher.publishEvent(new ArticleSearchSyncEvent(articleId));
    }
}
