package org.example.backend.service.core.article.impl;

import lombok.RequiredArgsConstructor;
import org.example.backend.model.entity.ArticleEntity;
import org.example.backend.model.vo.ArticleDetailVO;
import org.example.backend.model.vo.ArticleFavoriteVO;
import org.example.backend.model.vo.ArticleLikeVO;
import org.example.backend.model.vo.ArticleSummaryVO;
import org.example.backend.service.core.article.ArticleFeedCacheService;
import org.example.backend.service.core.article.support.ArticleFeedCardSupport;
import org.example.backend.service.core.article.support.ArticleFeedInteractionSupport;
import org.example.backend.service.core.article.support.ArticleFeedMaintenanceSupport;
import org.example.backend.service.core.article.support.ArticleFeedQuerySupport;
import org.example.backend.service.core.article.support.ArticleFeedStatsSupport;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticleFeedCacheServiceImpl implements ArticleFeedCacheService {

    private final ArticleFeedQuerySupport articleFeedQuerySupport;
    private final ArticleFeedCardSupport articleFeedCardSupport;
    private final ArticleFeedStatsSupport articleFeedStatsSupport;
    private final ArticleFeedInteractionSupport articleFeedInteractionSupport;
    private final ArticleFeedMaintenanceSupport articleFeedMaintenanceSupport;

    @Override
    public Page<ArticleSummaryVO> listFeed(String sortBy, int page, int size) {
        return articleFeedQuerySupport.listFeed(sortBy, page, size);
    }

    @Override
    public long recordView(ArticleEntity article) {
        return articleFeedInteractionSupport.recordView(article);
    }

    @Override
    public ArticleLikeVO toggleLike(Long userId, ArticleEntity article) {
        return articleFeedInteractionSupport.toggleLike(userId, article);
    }

    @Override
    public ArticleFavoriteVO toggleFavorite(Long userId, ArticleEntity article) {
        return articleFeedInteractionSupport.toggleFavorite(userId, article);
    }

    @Override
    public void applyStats(ArticleDetailVO detail) {
        articleFeedStatsSupport.applyStats(detail);
    }

    @Override
    public void applyCacheOverlay(List<ArticleSummaryVO> records) {
        articleFeedCardSupport.applyCard(records);
        articleFeedStatsSupport.applyStats(records);
    }

    @Override
    public boolean resolveLiked(Long userId, Long articleId, boolean mysqlFallbackLiked) {
        return articleFeedInteractionSupport.resolveLiked(userId, articleId, mysqlFallbackLiked);
    }

    @Override
    public boolean resolveFavorited(Long userId, Long articleId, boolean mysqlFallbackFavorited) {
        return articleFeedInteractionSupport.resolveFavorited(userId, articleId, mysqlFallbackFavorited);
    }

    @Override
    public boolean isLikeRelationInitialized(Long articleId) {
        return articleFeedInteractionSupport.isLikeRelationInitialized(articleId);
    }

    @Override
    public boolean isFavoriteRelationInitialized(Long articleId) {
        return articleFeedInteractionSupport.isFavoriteRelationInitialized(articleId);
    }

    @Override
    public void refreshArticle(Long articleId) {
        articleFeedMaintenanceSupport.refreshArticle(articleId);
    }

    @Override
    @Scheduled(fixedDelayString = "${app.article-feed-cache.rebuild-interval-ms:300000}")
    public void rebuildFeedCache() {
        articleFeedMaintenanceSupport.rebuildFeedCache();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpOnStartup() {
        rebuildFeedCache();
    }
}
