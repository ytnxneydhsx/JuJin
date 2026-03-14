package org.example.backend.service.core.article;

import org.example.backend.model.entity.ArticleEntity;
import org.example.backend.model.vo.ArticleDetailVO;
import org.example.backend.model.vo.ArticleFavoriteVO;
import org.example.backend.model.vo.ArticleLikeVO;
import org.example.backend.model.vo.ArticleSummaryVO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface ArticleFeedCacheService {

    Page<ArticleSummaryVO> listFeed(String sortBy, int page, int size);

    long recordView(ArticleEntity article);

    ArticleLikeVO toggleLike(Long userId, ArticleEntity article);

    ArticleFavoriteVO toggleFavorite(Long userId, ArticleEntity article);

    void applyStats(ArticleDetailVO detail);

    void applyCacheOverlay(List<ArticleSummaryVO> records);

    boolean resolveLiked(Long userId, Long articleId, boolean mysqlFallbackLiked);

    boolean resolveFavorited(Long userId, Long articleId, boolean mysqlFallbackFavorited);

    boolean isLikeRelationInitialized(Long articleId);

    boolean isFavoriteRelationInitialized(Long articleId);

    void refreshArticle(Long articleId);

    void rebuildFeedCache();
}
