package org.example.backend.service.core.article.support;

import lombok.RequiredArgsConstructor;
import org.example.backend.config.AppArticleFeedCacheProperties;
import org.example.backend.model.entity.ArticleEntity;
import org.example.backend.model.vo.ArticleDetailVO;
import org.example.backend.model.vo.ArticleSummaryVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ArticleFeedStatsSupport {

    private final AppArticleFeedCacheProperties cacheProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final ArticleFeedCacheKeys articleFeedCacheKeys;
    private final ArticleFeedStatsCodec articleFeedStatsCodec;

    public void applyStats(ArticleDetailVO detail) {
        if (detail == null || detail.getArticleId() == null) {
            return;
        }
        Map<Object, Object> values = stringRedisTemplate.opsForHash()
                .entries(articleFeedCacheKeys.statsHashKey(detail.getArticleId()));
        articleFeedStatsCodec.applyStats(detail, values);
    }

    public void applyStats(List<ArticleSummaryVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        records.forEach(this::applyStats);
    }

    public void ensureStatsHash(ArticleEntity article) {
        String hashKey = articleFeedCacheKeys.statsHashKey(article.getId());
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(hashKey))) {
            return;
        }
        refreshStatsHash(article, false);
    }

    public void refreshStatsHash(ArticleEntity article, boolean preserveHotStats) {
        String hashKey = articleFeedCacheKeys.statsHashKey(article.getId());
        Map<Object, Object> existing = preserveHotStats
                ? stringRedisTemplate.opsForHash().entries(hashKey)
                : Collections.emptyMap();
        stringRedisTemplate.opsForHash().putAll(
                hashKey,
                articleFeedStatsCodec.buildStatsFields(article, existing, preserveHotStats)
        );
    }

    public long resolveCurrentViewCount(ArticleEntity article) {
        Map<Object, Object> values = stringRedisTemplate.opsForHash()
                .entries(articleFeedCacheKeys.statsHashKey(article.getId()));
        return articleFeedStatsCodec.readLong(values, ArticleFeedStatsFields.VIEW_COUNT, article.getViewCount());
    }

    public Long readLastTouchedAt(Long articleId) {
        Map<Object, Object> values = stringRedisTemplate.opsForHash()
                .entries(articleFeedCacheKeys.statsHashKey(articleId));
        return articleFeedStatsCodec.readNullableLong(values, ArticleFeedStatsFields.LAST_TOUCHED_AT, null);
    }

    public void touch(Long articleId, long touchedAt) {
        String articleIdText = String.valueOf(articleId);
        String hashKey = articleFeedCacheKeys.statsHashKey(articleId);
        stringRedisTemplate.opsForHash().put(hashKey, ArticleFeedStatsFields.LAST_TOUCHED_AT, String.valueOf(touchedAt));
        stringRedisTemplate.opsForZSet().add(articleFeedCacheKeys.interactionActiveKey(), articleIdText, touchedAt);
    }

    public boolean isExpired(Long touchedAt, long now) {
        if (touchedAt == null || touchedAt <= 0L) {
            return false;
        }
        return now - touchedAt >= cacheProperties.getInteractionRetentionMs();
    }

    public void evictInteractionCache(Long articleId) {
        String articleIdText = String.valueOf(articleId);
        stringRedisTemplate.delete(articleFeedCacheKeys.statsHashKey(articleId));
        stringRedisTemplate.delete(articleFeedCacheKeys.likeUserSetKey(articleId));
        stringRedisTemplate.delete(articleFeedCacheKeys.favoriteUserSetKey(articleId));
        stringRedisTemplate.delete(articleFeedCacheKeys.likeInitKey(articleId));
        stringRedisTemplate.delete(articleFeedCacheKeys.favoriteInitKey(articleId));
        stringRedisTemplate.opsForZSet().remove(articleFeedCacheKeys.interactionActiveKey(), articleIdText);
    }

    private void applyStats(ArticleSummaryVO summary) {
        if (summary == null || summary.getArticleId() == null) {
            return;
        }
        Map<Object, Object> values = stringRedisTemplate.opsForHash()
                .entries(articleFeedCacheKeys.statsHashKey(summary.getArticleId()));
        articleFeedStatsCodec.applyStats(summary, values);
    }
}
