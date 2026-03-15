package org.example.backend.service.core.article.support;

import lombok.RequiredArgsConstructor;
import org.example.backend.common.constant.AppConstants.RelationStatus;
import org.example.backend.config.AppArticleFeedCacheProperties;
import org.example.backend.mapper.interaction.ArticleFavoriteMapper;
import org.example.backend.mapper.interaction.ArticleLikeMapper;
import org.example.backend.model.entity.ArticleEntity;
import org.example.backend.model.vo.ArticleFavoriteVO;
import org.example.backend.model.vo.ArticleLikeVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ArticleFeedInteractionSupport {

    private static final long INIT_LOCK_SECONDS = 5L;

    private final AppArticleFeedCacheProperties cacheProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final ArticleLikeMapper articleLikeMapper;
    private final ArticleFavoriteMapper articleFavoriteMapper;
    private final ArticleFeedCacheKeys articleFeedCacheKeys;
    private final ArticleFeedStatsSupport articleFeedStatsSupport;

    private final DefaultRedisScript<List> toggleCounterScript = buildToggleCounterScript();

    public long recordView(ArticleEntity article) {
        articleFeedStatsSupport.ensureStatsHash(article);

        String articleIdText = String.valueOf(article.getId());
        String hashKey = articleFeedCacheKeys.statsHashKey(article.getId());
        Long latestViewCount = stringRedisTemplate.opsForHash()
                .increment(hashKey, ArticleFeedStatsFields.VIEW_COUNT, 1L);
        long now = System.currentTimeMillis();
        articleFeedStatsSupport.touch(article.getId(), now);
        stringRedisTemplate.opsForSet().add(articleFeedCacheKeys.dirtyArticleSetKey(), articleIdText);
        long currentViewCount = latestViewCount == null ? 0L : Math.max(0L, latestViewCount);
        refreshRankingFeedAfterView(article.getId(), currentViewCount);
        return currentViewCount;
    }

    public ArticleLikeVO toggleLike(Long userId, ArticleEntity article) {
        articleFeedStatsSupport.ensureStatsHash(article);
        initializeLikeSetIfNeeded(article.getId());
        List<?> result = executeToggleCounterScript(
                articleFeedCacheKeys.likeUserSetKey(article.getId()),
                articleFeedCacheKeys.statsHashKey(article.getId()),
                String.valueOf(userId),
                String.valueOf(article.getId()),
                ArticleFeedStatsFields.LIKE_COUNT
        );
        boolean liked = asLong(result, 0) == 1L;
        long likeCount = Math.max(0L, asLong(result, 1));
        return ArticleLikeVO.builder()
                .articleId(article.getId())
                .liked(liked)
                .likeCount(likeCount)
                .build();
    }

    public ArticleFavoriteVO toggleFavorite(Long userId, ArticleEntity article) {
        articleFeedStatsSupport.ensureStatsHash(article);
        initializeFavoriteSetIfNeeded(article.getId());
        List<?> result = executeToggleCounterScript(
                articleFeedCacheKeys.favoriteUserSetKey(article.getId()),
                articleFeedCacheKeys.statsHashKey(article.getId()),
                String.valueOf(userId),
                String.valueOf(article.getId()),
                ArticleFeedStatsFields.FAVORITE_COUNT
        );
        boolean favorited = asLong(result, 0) == 1L;
        long favoriteCount = Math.max(0L, asLong(result, 1));
        return ArticleFavoriteVO.builder()
                .articleId(article.getId())
                .favorited(favorited)
                .favoriteCount(favoriteCount)
                .build();
    }

    public boolean resolveLiked(Long userId, Long articleId, boolean mysqlFallbackLiked) {
        if (userId == null || userId <= 0 || articleId == null || articleId <= 0) {
            return false;
        }
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(articleFeedCacheKeys.likeInitKey(articleId)))) {
            return mysqlFallbackLiked;
        }
        return Boolean.TRUE.equals(
                stringRedisTemplate.opsForSet().isMember(articleFeedCacheKeys.likeUserSetKey(articleId), String.valueOf(userId))
        );
    }

    public boolean resolveFavorited(Long userId, Long articleId, boolean mysqlFallbackFavorited) {
        if (userId == null || userId <= 0 || articleId == null || articleId <= 0) {
            return false;
        }
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(articleFeedCacheKeys.favoriteInitKey(articleId)))) {
            return mysqlFallbackFavorited;
        }
        return Boolean.TRUE.equals(
                stringRedisTemplate.opsForSet().isMember(articleFeedCacheKeys.favoriteUserSetKey(articleId), String.valueOf(userId))
        );
    }

    public boolean isLikeRelationInitialized(Long articleId) {
        if (articleId == null || articleId <= 0) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(articleFeedCacheKeys.likeInitKey(articleId)));
    }

    public boolean isFavoriteRelationInitialized(Long articleId) {
        if (articleId == null || articleId <= 0) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(articleFeedCacheKeys.favoriteInitKey(articleId)));
    }

    private void initializeLikeSetIfNeeded(Long articleId) {
        String initKey = articleFeedCacheKeys.likeInitKey(articleId);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(initKey))) {
            return;
        }
        String lockKey = articleFeedCacheKeys.likeInitLockKey(articleId);
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                lockKey,
                "1",
                Duration.ofSeconds(INIT_LOCK_SECONDS)
        );
        if (!Boolean.TRUE.equals(locked)) {
            waitForInitialization(initKey);
            return;
        }

        List<Long> userIds = articleLikeMapper.selectActiveUserIdsByArticleId(articleId, RelationStatus.ACTIVE);
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(initKey))) {
                return;
            }
            String setKey = articleFeedCacheKeys.likeUserSetKey(articleId);
            stringRedisTemplate.delete(setKey);
            if (!userIds.isEmpty()) {
                String[] values = userIds.stream().map(String::valueOf).toArray(String[]::new);
                stringRedisTemplate.opsForSet().add(setKey, values);
            }
            stringRedisTemplate.opsForValue().set(initKey, "1");
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    private void initializeFavoriteSetIfNeeded(Long articleId) {
        String initKey = articleFeedCacheKeys.favoriteInitKey(articleId);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(initKey))) {
            return;
        }
        String lockKey = articleFeedCacheKeys.favoriteInitLockKey(articleId);
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                lockKey,
                "1",
                Duration.ofSeconds(INIT_LOCK_SECONDS)
        );
        if (!Boolean.TRUE.equals(locked)) {
            waitForInitialization(initKey);
            return;
        }

        List<Long> userIds = articleFavoriteMapper.selectActiveUserIdsByArticleId(articleId, RelationStatus.ACTIVE);
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(initKey))) {
                return;
            }
            String setKey = articleFeedCacheKeys.favoriteUserSetKey(articleId);
            stringRedisTemplate.delete(setKey);
            if (!userIds.isEmpty()) {
                String[] values = userIds.stream().map(String::valueOf).toArray(String[]::new);
                stringRedisTemplate.opsForSet().add(setKey, values);
            }
            stringRedisTemplate.opsForValue().set(initKey, "1");
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    private List<?> executeToggleCounterScript(String relationSetKey,
                                               String hashKey,
                                               String userIdText,
                                               String articleIdText,
                                               String countField) {
        List<?> result = stringRedisTemplate.execute(
                toggleCounterScript,
                List.of(
                        relationSetKey,
                        hashKey,
                        articleFeedCacheKeys.dirtyArticleSetKey()
                ),
                userIdText,
                articleIdText,
                countField,
                String.valueOf(System.currentTimeMillis())
        );
        return result == null ? Collections.emptyList() : result;
    }

    private void waitForInitialization(String initKey) {
        for (int i = 0; i < 10; i++) {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(initKey))) {
                return;
            }
            try {
                Thread.sleep(20L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void refreshRankingFeedAfterView(Long articleId, long currentViewCount) {
        if (articleId == null || articleId <= 0) {
            return;
        }
        String rankingKey = articleFeedCacheKeys.rankingKey();
        String articleIdText = String.valueOf(articleId);
        double score = currentViewCount;

        if (stringRedisTemplate.opsForZSet().score(rankingKey, articleIdText) != null) {
            stringRedisTemplate.opsForZSet().add(rankingKey, articleIdText, score);
            trimRankingFeedIfNeeded(rankingKey);
            return;
        }

        Long size = stringRedisTemplate.opsForZSet().zCard(rankingKey);
        if (size == null || size < cacheProperties.getMaxItems()) {
            stringRedisTemplate.opsForZSet().add(rankingKey, articleIdText, score);
            trimRankingFeedIfNeeded(rankingKey);
            return;
        }

        Set<ZSetOperations.TypedTuple<String>> tail = stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores(rankingKey, cacheProperties.getMaxItems() - 1L, cacheProperties.getMaxItems() - 1L);
        if (tail == null || tail.isEmpty()) {
            stringRedisTemplate.opsForZSet().add(rankingKey, articleIdText, score);
            trimRankingFeedIfNeeded(rankingKey);
            return;
        }

        ZSetOperations.TypedTuple<String> threshold = tail.iterator().next();
        if (threshold.getScore() == null || score >= threshold.getScore()) {
            stringRedisTemplate.opsForZSet().add(rankingKey, articleIdText, score);
            trimRankingFeedIfNeeded(rankingKey);
        }
    }

    private void trimRankingFeedIfNeeded(String rankingKey) {
        Long size = stringRedisTemplate.opsForZSet().zCard(rankingKey);
        if (size == null || size <= cacheProperties.getMaxItems()) {
            return;
        }
        long overflow = size - cacheProperties.getMaxItems();
        stringRedisTemplate.opsForZSet().removeRange(rankingKey, 0, overflow - 1);
    }

    private long asLong(List<?> values, int index) {
        if (values == null || values.size() <= index) {
            return 0L;
        }
        Object value = values.get(index);
        if (value instanceof Number number) {
            return number.longValue();
        }
        Long parsed = parseLong(String.valueOf(value));
        return parsed == null ? 0L : parsed;
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private DefaultRedisScript<List> buildToggleCounterScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setResultType(List.class);
        script.setScriptText("""
                local relationSetKey = KEYS[1]
                local hashKey = KEYS[2]
                local dirtySetKey = KEYS[3]
                local userId = ARGV[1]
                local articleId = ARGV[2]
                local countField = ARGV[3]
                local nowMillis = ARGV[4]

                local active = 0
                local count = tonumber(redis.call('HGET', hashKey, countField) or '0')

                if redis.call('SISMEMBER', relationSetKey, userId) == 1 then
                    redis.call('SREM', relationSetKey, userId)
                    count = count - 1
                    if count < 0 then
                        count = 0
                    end
                    active = 0
                else
                    redis.call('SADD', relationSetKey, userId)
                    count = count + 1
                    active = 1
                end

                redis.call('HSET', hashKey, countField, tostring(count))
                redis.call('HSET', hashKey, 'lastTouchedAt', nowMillis)
                redis.call('SADD', dirtySetKey, articleId)
                return {active, count}
                """);
        return script;
    }
}
