package org.example.backend.service.core.article.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.common.constant.AppConstants.ArticleSort;
import org.example.backend.common.constant.AppConstants.RelationStatus;
import org.example.backend.config.AppArticleFeedCacheProperties;
import org.example.backend.mapper.article.ArticleMapper;
import org.example.backend.mapper.interaction.ArticleFavoriteMapper;
import org.example.backend.mapper.interaction.ArticleLikeMapper;
import org.example.backend.model.entity.ArticleEntity;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleFeedMaintenanceSupport {

    private static final int BATCH_UPSERT_SIZE = 500;

    private final AppArticleFeedCacheProperties cacheProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final ArticleMapper articleMapper;
    private final ArticleLikeMapper articleLikeMapper;
    private final ArticleFavoriteMapper articleFavoriteMapper;
    private final ArticleFeedCacheKeys articleFeedCacheKeys;
    private final ArticleFeedCardSupport articleFeedCardSupport;
    private final ArticleFeedStatsSupport articleFeedStatsSupport;
    private final ArticleFeedStatsCodec articleFeedStatsCodec;

    public void refreshArticle(Long articleId) {
        if (articleId == null || articleId <= 0) {
            return;
        }
        ArticleEntity article = articleMapper.selectPublishedCardById(articleId);
        if (article == null) {
            articleFeedCardSupport.evictArticle(articleId);
            articleFeedStatsSupport.evictInteractionCache(articleId);
            stringRedisTemplate.opsForSet().remove(articleFeedCacheKeys.dirtyArticleSetKey(), String.valueOf(articleId));
            return;
        }

        boolean cardExists = Boolean.TRUE.equals(stringRedisTemplate.hasKey(articleFeedCacheKeys.cardHashKey(articleId)));
        boolean statsExists = Boolean.TRUE.equals(stringRedisTemplate.hasKey(articleFeedCacheKeys.statsHashKey(articleId)));
        boolean inLatest = hasFeedMember(articleFeedCacheKeys.latestKey(), articleId);
        boolean inRanking = hasFeedMember(articleFeedCacheKeys.rankingKey(), articleId);
        boolean latestEligible = shouldEnterFeed(
                articleFeedCacheKeys.latestKey(),
                articleFeedCardSupport.publishedAtScore(article.getPublishedAt())
        );
        boolean rankingEligible = shouldEnterFeed(
                articleFeedCacheKeys.rankingKey(),
                articleFeedCardSupport.longScore(article.getViewCount())
        );
        boolean dirty = Boolean.TRUE.equals(
                stringRedisTemplate.opsForSet().isMember(articleFeedCacheKeys.dirtyArticleSetKey(), String.valueOf(articleId))
        );

        if (!cardExists && !statsExists && !dirty && !inLatest && !inRanking && !latestEligible && !rankingEligible) {
            return;
        }

        if (cardExists || inLatest || inRanking || latestEligible || rankingEligible) {
            articleFeedCardSupport.upsertCardHash(article);
        }
        if (!dirty && (statsExists || inLatest || inRanking || latestEligible || rankingEligible)) {
            articleFeedStatsSupport.refreshStatsHash(article);
        }
        if (inLatest || latestEligible) {
            stringRedisTemplate.opsForZSet().add(
                    articleFeedCacheKeys.latestKey(),
                    String.valueOf(articleId),
                    articleFeedCardSupport.publishedAtScore(article.getPublishedAt())
            );
        }
        if (inRanking || rankingEligible) {
            stringRedisTemplate.opsForZSet().add(
                    articleFeedCacheKeys.rankingKey(),
                    String.valueOf(articleId),
                    articleFeedCardSupport.longScore(articleFeedStatsSupport.resolveCurrentViewCount(article))
            );
        }
    }

    public void rebuildFeedCache() {
        try {
            Map<Long, Long> flushedArticles = flushDirtyArticlesToMysql();
            rebuildFeedBaselineFromMysql();
            replayDirtyArticlesToFeeds();
            cleanupColdInteractionCaches(flushedArticles);
        } catch (Exception ex) {
            log.error("rebuild article feed cache failed", ex);
        }
    }

    private Map<Long, Long> flushDirtyArticlesToMysql() {
        Map<Long, Long> flushedArticles = new LinkedHashMap<>();
        while (true) {
            String articleIdText = stringRedisTemplate.opsForSet().pop(articleFeedCacheKeys.dirtyArticleSetKey());
            if (!StringUtils.hasText(articleIdText)) {
                return flushedArticles;
            }
            Long articleId = parseLong(articleIdText);
            if (articleId == null) {
                continue;
            }
            Long lastTouchedAt = articleFeedStatsSupport.readLastTouchedAt(articleId);
            try {
                flushSingleDirtyArticle(articleId);
                flushedArticles.put(articleId, lastTouchedAt);
            } catch (Exception ex) {
                log.warn("flush dirty article cache failed, articleId={}", articleId, ex);
                stringRedisTemplate.opsForSet().add(articleFeedCacheKeys.dirtyArticleSetKey(), articleIdText);
            }
        }
    }

    private void flushSingleDirtyArticle(Long articleId) {
        String hashKey = articleFeedCacheKeys.statsHashKey(articleId);
        Map<Object, Object> values = stringRedisTemplate.opsForHash().entries(hashKey);
        if (values == null || values.isEmpty()) {
            return;
        }

        long viewCount = articleFeedStatsCodec.readLong(values, ArticleFeedStatsFields.VIEW_COUNT, 0L);
        long likeCount = articleFeedStatsCodec.readLong(values, ArticleFeedStatsFields.LIKE_COUNT, 0L);
        long favoriteCount = articleFeedStatsCodec.readLong(values, ArticleFeedStatsFields.FAVORITE_COUNT, 0L);

        articleMapper.ensureStatsByArticleId(articleId, 0L, 0L, 0L);
        articleMapper.updateViewCountById(articleId, viewCount);
        articleMapper.updateLikeCountById(articleId, likeCount);
        articleMapper.updateFavoriteCountById(articleId, favoriteCount);

        flushLikeRelationsIfInitialized(articleId);
        flushFavoriteRelationsIfInitialized(articleId);
    }

    private void flushLikeRelationsIfInitialized(Long articleId) {
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(articleFeedCacheKeys.likeInitKey(articleId)))) {
            return;
        }
        List<Long> userIds = readUserIdsFromSet(articleFeedCacheKeys.likeUserSetKey(articleId));
        articleLikeMapper.cancelAllActiveByArticleId(articleId, RelationStatus.CANCELLED, RelationStatus.ACTIVE);
        if (userIds.isEmpty()) {
            return;
        }
        for (List<Long> chunk : splitChunks(userIds)) {
            articleLikeMapper.batchUpsertStatusByArticleId(articleId, chunk, RelationStatus.ACTIVE);
        }
    }

    private void flushFavoriteRelationsIfInitialized(Long articleId) {
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(articleFeedCacheKeys.favoriteInitKey(articleId)))) {
            return;
        }
        List<Long> userIds = readUserIdsFromSet(articleFeedCacheKeys.favoriteUserSetKey(articleId));
        articleFavoriteMapper.cancelAllActiveByArticleId(articleId, RelationStatus.CANCELLED, RelationStatus.ACTIVE);
        if (userIds.isEmpty()) {
            return;
        }
        for (List<Long> chunk : splitChunks(userIds)) {
            articleFavoriteMapper.batchUpsertStatusByArticleId(articleId, chunk, RelationStatus.ACTIVE);
        }
    }

    private void rebuildFeedBaselineFromMysql() {
        List<ArticleEntity> latestArticles = articleMapper.selectPublishedPage(
                null,
                0,
                cacheProperties.getMaxItems(),
                ArticleSort.BY_PUBLISHED_AT,
                ArticleSort.ORDER_DESC
        );
        List<ArticleEntity> rankingArticles = articleMapper.selectPublishedPage(
                null,
                0,
                cacheProperties.getMaxItems(),
                ArticleSort.BY_VIEW_COUNT,
                ArticleSort.ORDER_DESC
        );

        String latestTmpKey = articleFeedCacheKeys.latestTempKey();
        String rankingTmpKey = articleFeedCacheKeys.rankingTempKey();
        stringRedisTemplate.delete(latestTmpKey);
        stringRedisTemplate.delete(rankingTmpKey);

        batchAddToZSet(
                latestTmpKey,
                latestArticles.stream()
                        .map(article -> new DefaultTypedTuple<>(
                                String.valueOf(article.getId()),
                                articleFeedCardSupport.publishedAtScore(article.getPublishedAt())
                        ))
                        .collect(Collectors.toSet())
        );
        batchAddToZSet(
                rankingTmpKey,
                rankingArticles.stream()
                        .map(article -> new DefaultTypedTuple<>(
                                String.valueOf(article.getId()),
                                articleFeedCardSupport.longScore(article.getViewCount())
                        ))
                        .collect(Collectors.toSet())
        );

        Set<String> dirtyArticles = stringRedisTemplate.opsForSet().members(articleFeedCacheKeys.dirtyArticleSetKey());
        Map<Long, ArticleEntity> unionArticles = new LinkedHashMap<>();
        latestArticles.forEach(article -> unionArticles.put(article.getId(), article));
        rankingArticles.forEach(article -> unionArticles.put(article.getId(), article));

        unionArticles.values().forEach(article -> {
            articleFeedCardSupport.upsertCardHash(article);
            if (dirtyArticles == null || !dirtyArticles.contains(String.valueOf(article.getId()))) {
                articleFeedStatsSupport.refreshStatsHash(article);
            }
        });

        if (latestArticles.isEmpty()) {
            stringRedisTemplate.delete(articleFeedCacheKeys.latestKey());
        } else {
            stringRedisTemplate.rename(latestTmpKey, articleFeedCacheKeys.latestKey());
        }
        if (rankingArticles.isEmpty()) {
            stringRedisTemplate.delete(articleFeedCacheKeys.rankingKey());
        } else {
            stringRedisTemplate.rename(rankingTmpKey, articleFeedCacheKeys.rankingKey());
        }
    }

    private void replayDirtyArticlesToFeeds() {
        Set<String> dirtyArticles = stringRedisTemplate.opsForSet().members(articleFeedCacheKeys.dirtyArticleSetKey());
        if (dirtyArticles == null || dirtyArticles.isEmpty()) {
            return;
        }
        for (String articleIdText : dirtyArticles) {
            Long articleId = parseLong(articleIdText);
            if (articleId == null) {
                continue;
            }
            refreshArticle(articleId);
        }
    }

    private void cleanupColdInteractionCaches(Map<Long, Long> flushedArticles) {
        if (flushedArticles.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<Long, Long> entry : flushedArticles.entrySet()) {
            Long articleId = entry.getKey();
            Long flushedTouchedAt = entry.getValue();
            if (articleId == null) {
                continue;
            }
            if (hasFeedMember(articleFeedCacheKeys.latestKey(), articleId)
                    || hasFeedMember(articleFeedCacheKeys.rankingKey(), articleId)) {
                continue;
            }
            if (Boolean.TRUE.equals(
                    stringRedisTemplate.opsForSet().isMember(articleFeedCacheKeys.dirtyArticleSetKey(), String.valueOf(articleId))
            )) {
                continue;
            }
            Long currentTouchedAt = articleFeedStatsSupport.readLastTouchedAt(articleId);
            if (!Objects.equals(flushedTouchedAt, currentTouchedAt)) {
                continue;
            }
            if (!articleFeedStatsSupport.isExpired(currentTouchedAt, now)) {
                continue;
            }
            articleFeedStatsSupport.evictInteractionCache(articleId);
        }
    }

    private boolean hasFeedMember(String key, Long articleId) {
        return stringRedisTemplate.opsForZSet().score(key, String.valueOf(articleId)) != null;
    }

    private boolean shouldEnterFeed(String key, double score) {
        Long size = stringRedisTemplate.opsForZSet().zCard(key);
        if (size == null || size < cacheProperties.getMaxItems()) {
            return true;
        }
        Set<ZSetOperations.TypedTuple<String>> tail = stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores(key, cacheProperties.getMaxItems() - 1L, cacheProperties.getMaxItems() - 1L);
        if (tail == null || tail.isEmpty()) {
            return true;
        }
        ZSetOperations.TypedTuple<String> tuple = tail.iterator().next();
        return tuple.getScore() == null || score >= tuple.getScore();
    }

    private List<Long> readUserIdsFromSet(String key) {
        Set<String> values = stringRedisTemplate.opsForSet().members(key);
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream()
                .map(this::parseLong)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<List<Long>> splitChunks(List<Long> values) {
        if (values.isEmpty()) {
            return Collections.emptyList();
        }
        List<List<Long>> chunks = new ArrayList<>();
        for (int i = 0; i < values.size(); i += BATCH_UPSERT_SIZE) {
            int end = Math.min(values.size(), i + BATCH_UPSERT_SIZE);
            chunks.add(values.subList(i, end));
        }
        return chunks;
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void batchAddToZSet(String key, Set<ZSetOperations.TypedTuple<String>> tuples) {
        if (tuples == null || tuples.isEmpty()) {
            return;
        }
        stringRedisTemplate.opsForZSet().add(key, tuples);
    }
}
