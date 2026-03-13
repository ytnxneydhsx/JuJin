package org.example.backend.service.core.interaction.impl;

import lombok.RequiredArgsConstructor;
import org.example.backend.exception.BizException;
import org.example.backend.mapper.article.ArticleMapper;
import org.example.backend.model.entity.ArticleEntity;
import org.example.backend.model.vo.ArticleFavoriteVO;
import org.example.backend.model.vo.ArticleLikeVO;
import org.example.backend.service.core.article.ArticleFeedCacheService;
import org.example.backend.service.core.interaction.ArticleInteractionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArticleInteractionServiceImpl implements ArticleInteractionService {

    private final ArticleMapper articleMapper;
    private final ArticleFeedCacheService articleFeedCacheService;

    @Override
    @Transactional
    public ArticleLikeVO likeArticle(Long userId, Long articleId) {
        validatePositive("userId", userId);
        validatePositive("articleId", articleId);
        ArticleEntity article = requirePublishedArticle(articleId);
        return articleFeedCacheService.toggleLike(userId, article);
    }

    @Override
    @Transactional
    public ArticleFavoriteVO favoriteArticle(Long userId, Long articleId) {
        validatePositive("userId", userId);
        validatePositive("articleId", articleId);
        ArticleEntity article = requirePublishedArticle(articleId);
        return articleFeedCacheService.toggleFavorite(userId, article);
    }

    private void validatePositive(String fieldName, Long value) {
        if (value == null || value <= 0) {
            throw new BizException("INVALID_PARAM", fieldName + " must be a positive number");
        }
    }

    private ArticleEntity requirePublishedArticle(Long articleId) {
        articleMapper.ensureStatsByArticleId(articleId, 0L, 0L, 0L);
        ArticleEntity article = articleMapper.selectPublishedCardById(articleId);
        if (article == null) {
            throw new BizException("ARTICLE_NOT_FOUND", "Article not found");
        }
        return article;
    }
}
