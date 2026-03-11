package org.example.backend.service.core.article.impl;

import lombok.RequiredArgsConstructor;
import org.example.backend.event.article.ArticleSearchSyncEvent;
import org.example.backend.exception.BizException;
import org.example.backend.mapper.article.ArticleMapper;
import org.example.backend.mapper.interaction.ArticleFavoriteMapper;
import org.example.backend.mapper.interaction.ArticleLikeMapper;
import org.example.backend.model.dto.article.UpdateArticleDTO;
import org.example.backend.model.entity.ArticleEntity;
import org.example.backend.model.vo.ArticleDetailVO;
import org.example.backend.model.vo.ArticleSummaryVO;
import org.example.backend.service.core.article.ArticleService;
import org.example.backend.service.core.article.ArticleViewCountService;
import org.example.backend.service.core.interaction.ArticleLikeCacheService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    private static final int STATUS_PUBLISHED = 1;
    private static final int STATUS_HIDDEN = 2;
    private static final int RELATION_ACTIVE = 1;
    private static final String SORT_BY_PUBLISHED_AT = "publishedAt";
    private static final String SORT_BY_VIEW_COUNT = "viewCount";
    private static final String SORT_ORDER_ASC = "asc";
    private static final String SORT_ORDER_DESC = "desc";

    private final ArticleMapper articleMapper;
    private final ArticleLikeMapper articleLikeMapper;
    private final ArticleFavoriteMapper articleFavoriteMapper;
    private final ArticleViewCountService articleViewCountService;
    private final ArticleLikeCacheService articleLikeCacheService;
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
        ArticleDetailVO detail = toDetailVO(article);
        detail.setLikeCount(articleLikeCacheService.getLikeCount(articleId, detail.getLikeCount()));
        applyInteractionState(detail, userId);
        return detail;
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
        applyLikeCount(records);
        applyInteractionState(records, userId);
        long total = articleMapper.countByUserId(userId);
        return new PageImpl<>(records, pageable, total);
    }

    @Override
    public ArticleDetailVO getPublishedArticle(Long viewerUserId, Long articleId) {
        validatePositive("articleId", articleId);
        ArticleEntity article = articleMapper.selectPublishedById(articleId);
        if (article == null) {
            throw new BizException("ARTICLE_NOT_FOUND", "Article not found");
        }
        long latestViewCount = articleViewCountService.increaseAndGet(articleId, article.getViewCount());
        ArticleDetailVO detail = toDetailVO(article);
        detail.setViewCount(latestViewCount);
        detail.setLikeCount(articleLikeCacheService.getLikeCount(articleId, detail.getLikeCount()));
        applyInteractionState(detail, viewerUserId);
        return detail;
    }

    @Override
    public Page<ArticleSummaryVO> listPublishedArticles(Long viewerUserId,
                                                        Long authorUserId,
                                                        String sortBy,
                                                        String sortOrder,
                                                        int page,
                                                        int size) {
        if (authorUserId != null && authorUserId <= 0) {
            throw new BizException("INVALID_PARAM", "userId must be a positive number");
        }
        Pageable pageable = requirePageable(page, size);
        int offset = Math.toIntExact(pageable.getOffset());
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortOrder = normalizeSortOrder(sortOrder);

        List<ArticleSummaryVO> records = articleMapper.selectPublishedPage(
                        authorUserId,
                        offset,
                        pageable.getPageSize(),
                        normalizedSortBy,
                        normalizedSortOrder
                )
                .stream()
                .map(this::toSummaryVO)
                .toList();
        applyLikeCount(records);
        applyInteractionState(records, viewerUserId);
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
                .likeCount(entity.getLikeCount())
                .favoriteCount(entity.getFavoriteCount())
                .viewCount(entity.getViewCount())
                .liked(false)
                .favorited(false)
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
                .likeCount(entity.getLikeCount())
                .favoriteCount(entity.getFavoriteCount())
                .viewCount(entity.getViewCount())
                .liked(false)
                .favorited(false)
                .publishedAt(entity.getPublishedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private void applyInteractionState(ArticleDetailVO detail, Long viewerUserId) {
        if (viewerUserId == null || viewerUserId <= 0) {
            return;
        }
        Integer likeStatus = articleLikeMapper.selectStatusByUserIdAndArticleId(viewerUserId, detail.getArticleId());
        Integer favoriteStatus = articleFavoriteMapper.selectStatusByUserIdAndArticleId(viewerUserId, detail.getArticleId());
        boolean mysqlLiked = likeStatus != null && likeStatus == RELATION_ACTIVE;
        detail.setLiked(articleLikeCacheService.resolveLiked(viewerUserId, detail.getArticleId(), mysqlLiked));
        detail.setFavorited(favoriteStatus != null && favoriteStatus == RELATION_ACTIVE);
    }

    private void applyInteractionState(List<ArticleSummaryVO> records, Long viewerUserId) {
        if (records.isEmpty() || viewerUserId == null || viewerUserId <= 0) {
            return;
        }

        List<Long> articleIds = records.stream().map(ArticleSummaryVO::getArticleId).toList();
        Set<Long> likedIds = new HashSet<>(articleLikeMapper.selectArticleIdsByUserIdAndStatus(
                viewerUserId,
                RELATION_ACTIVE,
                articleIds
        ));
        Set<Long> favoritedIds = new HashSet<>(articleFavoriteMapper.selectArticleIdsByUserIdAndStatus(
                viewerUserId,
                RELATION_ACTIVE,
                articleIds
        ));
        records.forEach(item -> {
            item.setLiked(articleLikeCacheService.resolveLiked(
                    viewerUserId,
                    item.getArticleId(),
                    likedIds.contains(item.getArticleId())
            ));
            item.setFavorited(favoritedIds.contains(item.getArticleId()));
        });
    }

    private void applyLikeCount(List<ArticleSummaryVO> records) {
        records.forEach(item -> item.setLikeCount(
                articleLikeCacheService.getLikeCount(item.getArticleId(), item.getLikeCount())
        ));
    }

    private void publishArticleSearchSyncEvent(Long articleId) {
        eventPublisher.publishEvent(new ArticleSearchSyncEvent(articleId));
    }

    private String normalizeSortBy(String sortBy) {
        if (!StringUtils.hasText(sortBy)) {
            return SORT_BY_PUBLISHED_AT;
        }
        String normalized = sortBy.trim();
        if (SORT_BY_PUBLISHED_AT.equals(normalized) || SORT_BY_VIEW_COUNT.equals(normalized)) {
            return normalized;
        }
        throw new BizException("INVALID_PARAM", "sortBy must be publishedAt or viewCount");
    }

    private String normalizeSortOrder(String sortOrder) {
        if (!StringUtils.hasText(sortOrder)) {
            return SORT_ORDER_DESC;
        }
        String normalized = sortOrder.trim().toLowerCase();
        if (SORT_ORDER_ASC.equals(normalized) || SORT_ORDER_DESC.equals(normalized)) {
            return normalized;
        }
        throw new BizException("INVALID_PARAM", "sortOrder must be asc or desc");
    }
}
