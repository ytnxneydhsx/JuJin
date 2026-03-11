package org.example.backend.service.core.interaction.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.config.AppArticleLikeCacheProperties;
import org.example.backend.mapper.article.ArticleMapper;
import org.example.backend.mapper.interaction.ArticleLikeMapper;
import org.example.backend.model.vo.ArticleLikeVO;
import org.example.backend.service.core.interaction.ArticleLikeCacheService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleLikeCacheServiceImpl implements ArticleLikeCacheService {

    private static final int RELATION_ACTIVE = 1;
    private static final int RELATION_CANCELLED = 0;
    private static final int UPSERT_CHUNK_SIZE = 500;
    private static final long INIT_LOCK_SECONDS = 5L;

    private final AppArticleLikeCacheProperties cacheProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final ArticleLikeMapper articleLikeMapper;
    private final ArticleMapper articleMapper;

    private final DefaultRedisScript<List> toggleLikeScript = buildToggleLikeScript();

    @Override
    public ArticleLikeVO toggleLike(Long userId, Long articleId, Long mysqlBaseLikeCount) {
        String userIdText = String.valueOf(userId);
        String likeSetKey = likeSetKey(articleId);
        String likeCountKey = likeCountKey(articleId);
        String likeInitKey = likeInitKey(articleId);

        initializeLikeSetIfNeeded(articleId, mysqlBaseLikeCount);
        List<?> result = stringRedisTemplate.execute(
                toggleLikeScript,
                List.of(likeSetKey, likeCountKey),
                userIdText,
                String.valueOf(ttlSeconds())
        );
        if (result == null || result.size() < 2) {
            boolean liked = resolveLiked(userId, articleId, false);
            long likeCount = getLikeCount(articleId, mysqlBaseLikeCount);
            markDirty(articleId);
            return ArticleLikeVO.builder()
                    .articleId(articleId)
                    .liked(liked)
                    .likeCount(likeCount)
                    .build();
        }

        boolean liked = asLong(result.get(0), 0L) == 1L;
        long likeCount = Math.max(0L, asLong(result.get(1), 0L));
        if (ttlSeconds() > 0) {
            stringRedisTemplate.expire(likeSetKey, ttl());
            stringRedisTemplate.expire(likeCountKey, ttl());
            stringRedisTemplate.expire(likeInitKey, ttl());
        }
        markDirty(articleId);
        return ArticleLikeVO.builder()
                .articleId(articleId)
                .liked(liked)
                .likeCount(likeCount)
                .build();
    }

    @Override
    public long getLikeCount(Long articleId, Long mysqlBaseLikeCount) {
        String key = likeCountKey(articleId);
        String value = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.hasText(value)) {
            if (ttlSeconds() > 0) {
                stringRedisTemplate.expire(key, ttl());
            }
            return Math.max(0L, parseLongOrDefault(value, 0L));
        }

        long base = normalizeBaseCount(mysqlBaseLikeCount);
        if (ttlSeconds() > 0) {
            stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(base), ttl());
            stringRedisTemplate.expire(key, ttl());
        } else {
            stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(base));
        }
        return base;
    }

    @Override
    public boolean resolveLiked(Long userId, Long articleId, boolean mysqlFallbackLiked) {
        String initKey = likeInitKey(articleId);
        Boolean initialized = stringRedisTemplate.hasKey(initKey);
        if (!Boolean.TRUE.equals(initialized)) {
            return mysqlFallbackLiked;
        }

        Boolean liked = stringRedisTemplate.opsForSet().isMember(likeSetKey(articleId), String.valueOf(userId));
        if (ttlSeconds() > 0) {
            stringRedisTemplate.expire(likeSetKey(articleId), ttl());
            stringRedisTemplate.expire(initKey, ttl());
        }
        return Boolean.TRUE.equals(liked);
    }

    @Scheduled(fixedDelayString = "${app.article-like-cache.flush-interval-ms:60000}")
    public void flushDirtyLikesToMysql() {
        int limit = Math.max(1, cacheProperties.getFlushBatchSize());
        for (int i = 0; i < limit; i++) {
            String articleIdText = stringRedisTemplate.opsForSet().pop(cacheProperties.getDirtyArticleSetKey());
            if (!StringUtils.hasText(articleIdText)) {
                break;
            }
            Long articleId = parseArticleId(articleIdText);
            if (articleId == null) {
                continue;
            }
            try {
                flushSingleArticle(articleId);
            } catch (Exception ex) {
                log.warn("Flush article likes failed, articleId={}", articleId, ex);
                markDirty(articleId);
            }
        }
    }

    private void flushSingleArticle(Long articleId) {
        Set<String> memberValues = stringRedisTemplate.opsForSet().members(likeSetKey(articleId));
        List<Long> likedUserIds = memberValues == null ? Collections.emptyList() : memberValues.stream()
                .map(this::parseArticleId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());

        long likeCount = resolveLikeCountForFlush(articleId, likedUserIds.size());

        articleLikeMapper.cancelAllActiveByArticleId(articleId, RELATION_CANCELLED, RELATION_ACTIVE);
        if (!likedUserIds.isEmpty()) {
            for (List<Long> chunk : splitChunks(likedUserIds, UPSERT_CHUNK_SIZE)) {
                articleLikeMapper.batchUpsertStatusByArticleId(articleId, chunk, RELATION_ACTIVE);
            }
        }
        articleMapper.ensureStatsByArticleId(articleId, 0L, 0L, 0L);
        articleMapper.updateLikeCountById(articleId, likeCount);
    }

    private long resolveLikeCountForFlush(Long articleId, int fallbackCount) {
        String value = stringRedisTemplate.opsForValue().get(likeCountKey(articleId));
        if (!StringUtils.hasText(value)) {
            return Math.max(0, fallbackCount);
        }
        return Math.max(0L, parseLongOrDefault(value, fallbackCount));
    }

    private void initializeLikeSetIfNeeded(Long articleId, Long mysqlBaseLikeCount) {
        String initKey = likeInitKey(articleId);
        Boolean initialized = stringRedisTemplate.hasKey(initKey);
        if (Boolean.TRUE.equals(initialized)) {
            if (ttlSeconds() > 0) {
                stringRedisTemplate.expire(initKey, ttl());
            }
            return;
        }

        String lockKey = initKey + ":lock";
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(INIT_LOCK_SECONDS));
        if (!Boolean.TRUE.equals(locked)) {
            waitForInitialization(initKey);
            return;
        }

        try {
            Boolean doubleChecked = stringRedisTemplate.hasKey(initKey);
            if (Boolean.TRUE.equals(doubleChecked)) {
                if (ttlSeconds() > 0) {
                    stringRedisTemplate.expire(initKey, ttl());
                }
                return;
            }

            List<Long> likedUserIds = articleLikeMapper.selectActiveUserIdsByArticleId(articleId, RELATION_ACTIVE);
            String setKey = likeSetKey(articleId);
            stringRedisTemplate.delete(setKey);
            if (!likedUserIds.isEmpty()) {
                String[] values = likedUserIds.stream().map(String::valueOf).toArray(String[]::new);
                stringRedisTemplate.opsForSet().add(setKey, values);
            }

            long baseCount = likedUserIds.size();
            String countKey = likeCountKey(articleId);
            stringRedisTemplate.opsForValue().set(countKey, String.valueOf(baseCount));
            stringRedisTemplate.opsForValue().set(initKey, "1");

            if (ttlSeconds() > 0) {
                stringRedisTemplate.expire(setKey, ttl());
                stringRedisTemplate.expire(countKey, ttl());
                stringRedisTemplate.expire(initKey, ttl());
            }
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    private void waitForInitialization(String initKey) {
        for (int i = 0; i < 10; i++) {
            Boolean initialized = stringRedisTemplate.hasKey(initKey);
            if (Boolean.TRUE.equals(initialized)) {
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

    private void markDirty(Long articleId) {
        stringRedisTemplate.opsForSet().add(cacheProperties.getDirtyArticleSetKey(), String.valueOf(articleId));
    }

    private String likeSetKey(Long articleId) {
        return cacheProperties.getLikeSetKeyPrefix() + articleId;
    }

    private String likeCountKey(Long articleId) {
        return cacheProperties.getLikeCountKeyPrefix() + articleId;
    }

    private String likeInitKey(Long articleId) {
        return cacheProperties.getLikeInitKeyPrefix() + articleId;
    }

    private long ttlSeconds() {
        return cacheProperties.getTtlSeconds();
    }

    private Duration ttl() {
        return Duration.ofSeconds(ttlSeconds());
    }

    private Long parseArticleId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private long parseLongOrDefault(String value, long defaultValue) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private long asLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return parseLongOrDefault(String.valueOf(value), defaultValue);
    }

    private long normalizeBaseCount(Long mysqlBaseLikeCount) {
        return Math.max(0L, mysqlBaseLikeCount == null ? 0L : mysqlBaseLikeCount);
    }

    private List<List<Long>> splitChunks(List<Long> source, int chunkSize) {
        if (source.isEmpty()) {
            return Collections.emptyList();
        }
        List<List<Long>> chunks = new ArrayList<>();
        for (int i = 0; i < source.size(); i += chunkSize) {
            int end = Math.min(source.size(), i + chunkSize);
            chunks.add(source.subList(i, end));
        }
        return chunks;
    }

    private DefaultRedisScript<List> buildToggleLikeScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setResultType(List.class);
        script.setScriptText("""
                local setKey = KEYS[1]
                local countKey = KEYS[2]
                local userId = ARGV[1]
                local ttl = tonumber(ARGV[2])

                local liked = 0
                local likeCount = 0

                if redis.call('SISMEMBER', setKey, userId) == 1 then
                    redis.call('SREM', setKey, userId)
                    likeCount = redis.call('DECR', countKey)
                    if tonumber(likeCount) < 0 then
                        redis.call('SET', countKey, '0')
                        likeCount = 0
                    end
                    liked = 0
                else
                    redis.call('SADD', setKey, userId)
                    likeCount = redis.call('INCR', countKey)
                    liked = 1
                end

                if ttl ~= nil and ttl > 0 then
                    redis.call('EXPIRE', setKey, ttl)
                    redis.call('EXPIRE', countKey, ttl)
                end
                return {liked, likeCount}
                """);
        return script;
    }
}
