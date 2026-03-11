package org.example.backend.service.core.interaction.impl;

import lombok.RequiredArgsConstructor;
import org.example.backend.exception.BizException;
import org.example.backend.mapper.article.ArticleMapper;
import org.example.backend.mapper.interaction.ArticleFavoriteMapper;
import org.example.backend.model.entity.ArticleEntity;
import org.example.backend.model.vo.ArticleFavoriteVO;
import org.example.backend.model.vo.ArticleLikeVO;
import org.example.backend.service.core.interaction.ArticleInteractionService;
import org.example.backend.service.core.interaction.ArticleLikeCacheService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArticleInteractionServiceImpl implements ArticleInteractionService {

    private static final int RELATION_ACTIVE = 1;
    private static final int RELATION_CANCELLED = 0;

    private final ArticleMapper articleMapper;
    private final ArticleFavoriteMapper articleFavoriteMapper;
    private final ArticleLikeCacheService articleLikeCacheService;

    @Override
    @Transactional
    public ArticleLikeVO likeArticle(Long userId, Long articleId) {
        validatePositive("userId", userId);
        validatePositive("articleId", articleId);
        ArticleEntity stats = requireInteractionStats(articleId);
        return articleLikeCacheService.toggleLike(userId, articleId, toNonNullCount(stats.getLikeCount()));
    }

    @Override
    @Transactional
    public ArticleFavoriteVO favoriteArticle(Long userId, Long articleId) {
        validatePositive("userId", userId);
        validatePositive("articleId", articleId);
        requireInteractionStats(articleId);

        try {
            articleFavoriteMapper.insert(userId, articleId, RELATION_ACTIVE);
            incrementFavoriteCountOrThrow(articleId);
            return buildFavoriteVO(userId, articleId);
        } catch (DuplicateKeyException ex) {
            int toCancelled = articleFavoriteMapper.updateStatusByUserIdAndArticleId(
                    userId,
                    articleId,
                    RELATION_CANCELLED,
                    RELATION_ACTIVE
            );
            if (toCancelled == 1) {
                decrementFavoriteCountOrThrow(articleId);
                return buildFavoriteVO(userId, articleId);
            }

            int toActive = articleFavoriteMapper.updateStatusByUserIdAndArticleId(
                    userId,
                    articleId,
                    RELATION_ACTIVE,
                    RELATION_CANCELLED
            );
            if (toActive == 1) {
                incrementFavoriteCountOrThrow(articleId);
                return buildFavoriteVO(userId, articleId);
            }

            throw new BizException("ARTICLE_FAVORITE_FAILED", "Failed to toggle favorite status");
        }
    }

    private void validatePositive(String fieldName, Long value) {
        if (value == null || value <= 0) {
            throw new BizException("INVALID_PARAM", fieldName + " must be a positive number");
        }
    }

    private ArticleFavoriteVO buildFavoriteVO(Long userId, Long articleId) {
        Integer status = articleFavoriteMapper.selectStatusByUserIdAndArticleId(userId, articleId);
        ArticleEntity stats = requireInteractionStats(articleId);
        return ArticleFavoriteVO.builder()
                .articleId(articleId)
                .favorited(status != null && status == RELATION_ACTIVE)
                .favoriteCount(toNonNullCount(stats.getFavoriteCount()))
                .build();
    }

    private ArticleEntity requireInteractionStats(Long articleId) {
        ArticleEntity stats = articleMapper.selectInteractionStatsById(articleId);
        if (stats == null) {
            throw new BizException("ARTICLE_NOT_FOUND", "Article not found");
        }
        return stats;
    }

    private long toNonNullCount(Long count) {
        return count == null ? 0L : count;
    }

    private void incrementFavoriteCountOrThrow(Long articleId) {
        if (articleMapper.incrementFavoriteCountById(articleId) != 1) {
            throw new BizException("ARTICLE_FAVORITE_FAILED", "Failed to update favorite count");
        }
    }

    private void decrementFavoriteCountOrThrow(Long articleId) {
        if (articleMapper.decrementFavoriteCountById(articleId) != 1) {
            throw new BizException("ARTICLE_FAVORITE_FAILED", "Failed to update favorite count");
        }
    }
}
