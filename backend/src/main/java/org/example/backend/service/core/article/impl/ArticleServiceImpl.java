package org.example.backend.service.core.article.impl;

import lombok.RequiredArgsConstructor;
import org.example.backend.event.article.ArticleSearchSyncEvent;
import org.example.backend.exception.BizException;
import org.example.backend.mapper.article.ArticleMapper;
import org.example.backend.model.dto.article.UpdateArticleDTO;
import org.example.backend.model.entity.ArticleEntity;
import org.example.backend.model.vo.ArticleDetailVO;
import org.example.backend.model.vo.ArticleSummaryVO;
import org.example.backend.service.core.article.ArticleService;
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
                content,
                status
        );
        if (affected != 1) {
            throw new BizException("ARTICLE_NOT_FOUND", "Article not found");
        }
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

    @Override
    public ArticleDetailVO getMyArticle(Long userId, Long articleId) {
        validateUserId(userId);
        validatePositive("articleId", articleId);
        ArticleEntity article = articleMapper.selectByIdAndUserId(articleId, userId);
        if (article == null) {
            throw new BizException("ARTICLE_NOT_FOUND", "Article not found");
        }
        return toDetailVO(article);
    }

    @Override
    public Page<ArticleSummaryVO> listMyArticles(Long userId, int page, int size) {
        validateUserId(userId);
        Pageable pageable = requirePageable(page, size);
        int offset = Math.toIntExact(pageable.getOffset());

        List<ArticleSummaryVO> records = articleMapper.selectPageByUserId(userId, offset, pageable.getPageSize())
                .stream()
                .map(this::toSummaryVO)
                .toList();
        long total = articleMapper.countByUserId(userId);
        return new PageImpl<>(records, pageable, total);
    }

    @Override
    public ArticleDetailVO getPublishedArticle(Long articleId) {
        validatePositive("articleId", articleId);
        ArticleEntity article = articleMapper.selectPublishedById(articleId);
        if (article == null) {
            throw new BizException("ARTICLE_NOT_FOUND", "Article not found");
        }
        return toDetailVO(article);
    }

    @Override
    public Page<ArticleSummaryVO> listPublishedArticles(Long authorUserId, int page, int size) {
        if (authorUserId != null && authorUserId <= 0) {
            throw new BizException("INVALID_PARAM", "userId must be a positive number");
        }
        Pageable pageable = requirePageable(page, size);
        int offset = Math.toIntExact(pageable.getOffset());

        List<ArticleSummaryVO> records = articleMapper.selectPublishedPage(authorUserId, offset, pageable.getPageSize())
                .stream()
                .map(this::toSummaryVO)
                .toList();
        long total = articleMapper.countPublished(authorUserId);
        return new PageImpl<>(records, pageable, total);
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

    private ArticleSummaryVO toSummaryVO(ArticleEntity entity) {
        return ArticleSummaryVO.builder()
                .articleId(entity.getId())
                .userId(entity.getUserId())
                .title(entity.getTitle())
                .summary(entity.getSummary())
                .coverUrl(entity.getCoverUrl())
                .publishedAt(entity.getPublishedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private ArticleDetailVO toDetailVO(ArticleEntity entity) {
        return ArticleDetailVO.builder()
                .articleId(entity.getId())
                .userId(entity.getUserId())
                .title(entity.getTitle())
                .summary(entity.getSummary())
                .coverUrl(entity.getCoverUrl())
                .content(entity.getContent())
                .status(entity.getStatus())
                .publishedAt(entity.getPublishedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private void publishArticleSearchSyncEvent(Long articleId) {
        eventPublisher.publishEvent(new ArticleSearchSyncEvent(articleId));
    }
}
