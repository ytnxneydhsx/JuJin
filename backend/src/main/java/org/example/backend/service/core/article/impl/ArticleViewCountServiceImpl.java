package org.example.backend.service.core.article.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.config.AppViewCountCacheProperties;
import org.example.backend.mapper.article.ArticleMapper;
import org.example.backend.service.core.article.ArticleViewCountService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleViewCountServiceImpl implements ArticleViewCountService {

    private final AppViewCountCacheProperties cacheProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final ArticleMapper articleMapper;

    @Override
    public long increaseAndGet(Long articleId, Long mysqlBaseCount) {
        String key = buildKey(articleId);
        Boolean exists = stringRedisTemplate.hasKey(key);
        if (Boolean.FALSE.equals(exists)) {
            long base = mysqlBaseCount == null ? 0L : mysqlBaseCount;
            stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(base), ttl());
        }
        Long viewCount = stringRedisTemplate.opsForValue().increment(key);
        stringRedisTemplate.expire(key, ttl());
        return viewCount == null ? (mysqlBaseCount == null ? 0L : mysqlBaseCount) : viewCount;
    }

    @Override
    public long getViewCount(Long articleId, Long mysqlBaseCount) {
        String key = buildKey(articleId);
        String value = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.hasText(value)) {
            if (cacheProperties.getTtlSeconds() > 0) {
                stringRedisTemplate.expire(key, ttl());
            }
            try {
                return Math.max(0L, Long.parseLong(value));
            } catch (NumberFormatException ex) {
                log.warn("Skip invalid cached view count, articleId={}, value={}", articleId, value);
            }
        }
        return mysqlBaseCount == null ? 0L : Math.max(0L, mysqlBaseCount);
    }

    @Scheduled(fixedDelayString = "${app.view-count-cache.flush-interval-ms:60000}")
    public void flushToMysql() {
        Set<String> keys = stringRedisTemplate.keys(cacheProperties.getKeyPrefix() + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : keys) {
            Long articleId = extractArticleId(key);
            if (articleId == null) {
                continue;
            }
            String value = stringRedisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(value)) {
                continue;
            }
            try {
                long viewCount = Long.parseLong(value);
                articleMapper.ensureStatsByArticleId(articleId, 0L, 0L, 0L);
                articleMapper.updateViewCountById(articleId, viewCount);
            } catch (NumberFormatException ex) {
                log.warn("Skip flush invalid view count value, key={}, value={}", key, value);
            } catch (Exception ex) {
                log.warn("Flush view count failed, key={}, value={}", key, value, ex);
            }
        }
    }

    private String buildKey(Long articleId) {
        return cacheProperties.getKeyPrefix() + articleId;
    }

    private Duration ttl() {
        return Duration.ofSeconds(cacheProperties.getTtlSeconds());
    }

    private Long extractArticleId(String key) {
        if (!StringUtils.hasText(key) || !key.startsWith(cacheProperties.getKeyPrefix())) {
            return null;
        }
        String articleIdText = key.substring(cacheProperties.getKeyPrefix().length());
        if (!StringUtils.hasText(articleIdText)) {
            return null;
        }
        try {
            return Long.parseLong(articleIdText);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
