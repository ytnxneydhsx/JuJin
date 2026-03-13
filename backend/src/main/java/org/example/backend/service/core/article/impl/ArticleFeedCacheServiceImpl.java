package org.example.backend.service.core.article.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.common.page.PageUtils;
import org.example.backend.config.AppArticleFeedCacheProperties;
import org.example.backend.mapper.article.ArticleMapper;
import org.example.backend.mapper.interaction.ArticleFavoriteMapper;
import org.example.backend.mapper.interaction.ArticleLikeMapper;
import org.example.backend.model.entity.ArticleEntity;
import org.example.backend.model.vo.ArticleDetailVO;
import org.example.backend.model.vo.ArticleFavoriteVO;
import org.example.backend.model.vo.ArticleLikeVO;
import org.example.backend.model.vo.ArticleSummaryVO;
import org.example.backend.service.core.article.ArticleFeedCacheService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleFeedCacheServiceImpl implements ArticleFeedCacheService {

    private static final int RELATION_ACTIVE = 1;
    private static final int RELATION_CANCELLED = 0;
    private static final int BATCH_UPSERT_SIZE = 500;
    private static final long INIT_LOCK_SECONDS = 5L;
    private static final String SORT_BY_PUBLISHED_AT = "publishedAt";
    private static final String SORT_BY_VIEW_COUNT = "viewCount";
    private static final String FIELD_ARTICLE_ID = "articleId";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_SUMMARY = "summary";
    private static final String FIELD_COVER_URL = "coverUrl";
    private static final String FIELD_VIEW_COUNT = "viewCount";
    private static final String FIELD_LIKE_COUNT = "likeCount";
    private static final String FIELD_FAVORITE_COUNT = "favoriteCount";
    private static final String FIELD_PUBLISHED_AT = "publishedAt";
    private static final String FIELD_UPDATED_AT = "updatedAt";
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final AppArticleFeedCacheProperties cacheProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final ArticleMapper articleMapper;
    private final ArticleLikeMapper articleLikeMapper;
    private final ArticleFavoriteMapper articleFavoriteMapper;

    private final DefaultRedisScript<List> toggleCounterScript = buildToggleCounterScript();

    @Override
    public Page<ArticleSummaryVO> listFeed(String sortBy, int page, int size) {
        Pageable pageable = PageUtils.pageable(page, size);
        String zsetKey = feedKey(sortBy);
        Long total = stringRedisTemplate.opsForZSet().zCard(zsetKey);
        if (total == null || total <= 0L) {
            return PageUtils.page(Collections.emptyList(), pageable, 0L);
        }

        int start = PageUtils.offset(pageable);
        int end = start + pageable.getPageSize() - 1;
        Set<String> articleIdTexts = stringRedisTemplate.opsForZSet().reverseRange(zsetKey, start, end);
        if (articleIdTexts == null || articleIdTexts.isEmpty()) {
            return PageUtils.page(Collections.emptyList(), pageable, total);
        }

        List<ArticleSummaryVO> records = new ArrayList<>(articleIdTexts.size());
        for (String articleIdText : articleIdTexts) {
            Long articleId = parseLong(articleIdText);
            if (articleId == null) {
                continue;
            }
            ArticleSummaryVO summary = getOrLoadSummary(articleId);
            if (summary != null) {
                records.add(summary);
            }
        }
        return PageUtils.page(records, pageable, total);
    }

    @Override
    public long recordView(ArticleEntity article) {
        ensureCardHash(article);
        promoteToFeeds(article);

        String articleIdText = String.valueOf(article.getId());
        String hashKey = cardHashKey(article.getId());
        Long latestViewCount = stringRedisTemplate.opsForHash().increment(hashKey, FIELD_VIEW_COUNT, 1L);
        stringRedisTemplate.opsForZSet().incrementScore(rankingKey(), articleIdText, 1D);
        stringRedisTemplate.opsForSet().add(dirtyArticleSetKey(), articleIdText);
        return latestViewCount == null ? 0L : Math.max(0L, latestViewCount);
    }

    @Override
    public ArticleLikeVO toggleLike(Long userId, ArticleEntity article) {
        ensureCardHash(article);
        initializeLikeSetIfNeeded(article.getId());
        List<?> result = executeToggleCounterScript(
                likeUserSetKey(article.getId()),
                cardHashKey(article.getId()),
                String.valueOf(userId),
                String.valueOf(article.getId()),
                FIELD_LIKE_COUNT
        );
        boolean liked = asLong(result, 0) == 1L;
        long likeCount = Math.max(0L, asLong(result, 1));
        return ArticleLikeVO.builder()
                .articleId(article.getId())
                .liked(liked)
                .likeCount(likeCount)
                .build();
    }

    @Override
    public ArticleFavoriteVO toggleFavorite(Long userId, ArticleEntity article) {
        ensureCardHash(article);
        initializeFavoriteSetIfNeeded(article.getId());
        List<?> result = executeToggleCounterScript(
                favoriteUserSetKey(article.getId()),
                cardHashKey(article.getId()),
                String.valueOf(userId),
                String.valueOf(article.getId()),
                FIELD_FAVORITE_COUNT
        );
        boolean favorited = asLong(result, 0) == 1L;
        long favoriteCount = Math.max(0L, asLong(result, 1));
        return ArticleFavoriteVO.builder()
                .articleId(article.getId())
                .favorited(favorited)
                .favoriteCount(favoriteCount)
                .build();
    }

    @Override
    public void applyStats(ArticleDetailVO detail) {
        if (detail == null || detail.getArticleId() == null) {
            return;
        }
        Map<Object, Object> values = stringRedisTemplate.opsForHash().entries(cardHashKey(detail.getArticleId()));
        if (values == null || values.isEmpty()) {
            return;
        }
        detail.setViewCount(readLong(values, FIELD_VIEW_COUNT, detail.getViewCount()));
        detail.setLikeCount(readLong(values, FIELD_LIKE_COUNT, detail.getLikeCount()));
        detail.setFavoriteCount(readLong(values, FIELD_FAVORITE_COUNT, detail.getFavoriteCount()));
        detail.setSummary(readText(values, FIELD_SUMMARY, detail.getSummary()));
        detail.setCoverUrl(readText(values, FIELD_COVER_URL, detail.getCoverUrl()));
        detail.setPublishedAt(readDateTime(values, FIELD_PUBLISHED_AT, detail.getPublishedAt()));
        detail.setUpdatedAt(readDateTime(values, FIELD_UPDATED_AT, detail.getUpdatedAt()));
    }

    @Override
    public void applyStats(List<ArticleSummaryVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        records.forEach(this::applyStats);
    }

    @Override
    public boolean resolveLiked(Long userId, Long articleId, boolean mysqlFallbackLiked) {
        if (userId == null || userId <= 0 || articleId == null || articleId <= 0) {
            return false;
        }
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(likeInitKey(articleId)))) {
            return mysqlFallbackLiked;
        }
        return Boolean.TRUE.equals(
                stringRedisTemplate.opsForSet().isMember(likeUserSetKey(articleId), String.valueOf(userId))
        );
    }

    @Override
    public boolean resolveFavorited(Long userId, Long articleId, boolean mysqlFallbackFavorited) {
        if (userId == null || userId <= 0 || articleId == null || articleId <= 0) {
            return false;
        }
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(favoriteInitKey(articleId)))) {
            return mysqlFallbackFavorited;
        }
        return Boolean.TRUE.equals(
                stringRedisTemplate.opsForSet().isMember(favoriteUserSetKey(articleId), String.valueOf(userId))
        );
    }

    @Override
    public void refreshArticle(Long articleId) {
        if (articleId == null || articleId <= 0) {
            return;
        }
        ArticleEntity article = articleMapper.selectPublishedCardById(articleId);
        if (article == null) {
            evictArticle(articleId);
            return;
        }

        boolean hashExists = Boolean.TRUE.equals(stringRedisTemplate.hasKey(cardHashKey(articleId)));
        boolean inLatest = hasFeedMember(latestKey(), articleId);
        boolean inRanking = hasFeedMember(rankingKey(), articleId);
        boolean latestEligible = shouldEnterFeed(latestKey(), publishedAtScore(article.getPublishedAt()));
        boolean rankingEligible = shouldEnterFeed(rankingKey(), toDouble(article.getViewCount()));
        boolean dirty = Boolean.TRUE.equals(
                stringRedisTemplate.opsForSet().isMember(dirtyArticleSetKey(), String.valueOf(articleId))
        );

        if (!hashExists && !inLatest && !inRanking && !latestEligible && !rankingEligible) {
            return;
        }

        upsertCardHash(article, hashExists || dirty);
        if (inLatest || latestEligible) {
            stringRedisTemplate.opsForZSet().add(latestKey(), String.valueOf(articleId), publishedAtScore(article.getPublishedAt()));
        }
        if (inRanking || rankingEligible) {
            stringRedisTemplate.opsForZSet().add(rankingKey(), String.valueOf(articleId), toDouble(resolveCurrentViewCount(article)));
        }
    }

    @Override
    @Scheduled(fixedDelayString = "${app.article-feed-cache.rebuild-interval-ms:300000}")
    public void rebuildFeedCache() {
        try {
            flushDirtyArticlesToMysql();
            rebuildFeedBaselineFromMysql();
            replayDirtyArticlesToFeeds();
        } catch (Exception ex) {
            log.error("rebuild article feed cache failed", ex);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpOnStartup() {
        rebuildFeedCache();
    }

    private void applyStats(ArticleSummaryVO summary) {
        if (summary == null || summary.getArticleId() == null) {
            return;
        }
        Map<Object, Object> values = stringRedisTemplate.opsForHash().entries(cardHashKey(summary.getArticleId()));
        if (values == null || values.isEmpty()) {
            return;
        }
        summary.setViewCount(readLong(values, FIELD_VIEW_COUNT, summary.getViewCount()));
        summary.setLikeCount(readLong(values, FIELD_LIKE_COUNT, summary.getLikeCount()));
        summary.setFavoriteCount(readLong(values, FIELD_FAVORITE_COUNT, summary.getFavoriteCount()));
        summary.setSummary(readText(values, FIELD_SUMMARY, summary.getSummary()));
        summary.setCoverUrl(readText(values, FIELD_COVER_URL, summary.getCoverUrl()));
        summary.setPublishedAt(readDateTime(values, FIELD_PUBLISHED_AT, summary.getPublishedAt()));
        summary.setUpdatedAt(readDateTime(values, FIELD_UPDATED_AT, summary.getUpdatedAt()));
    }

    private void ensureCardHash(ArticleEntity article) {
        String hashKey = cardHashKey(article.getId());
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(hashKey))) {
            return;
        }
        upsertCardHash(article, false);
    }

    private void promoteToFeeds(ArticleEntity article) {
        String articleIdText = String.valueOf(article.getId());
        stringRedisTemplate.opsForZSet().add(latestKey(), articleIdText, publishedAtScore(article.getPublishedAt()));
        stringRedisTemplate.opsForZSet().add(rankingKey(), articleIdText, toDouble(resolveCurrentViewCount(article)));
    }

    private void evictArticle(Long articleId) {
        String articleIdText = String.valueOf(articleId);
        stringRedisTemplate.opsForZSet().remove(latestKey(), articleIdText);
        stringRedisTemplate.opsForZSet().remove(rankingKey(), articleIdText);
        stringRedisTemplate.delete(cardHashKey(articleId));
    }

    private ArticleSummaryVO getOrLoadSummary(Long articleId) {
        Map<Object, Object> values = stringRedisTemplate.opsForHash().entries(cardHashKey(articleId));
        if (values == null || values.isEmpty()) {
            ArticleEntity article = articleMapper.selectPublishedCardById(articleId);
            if (article == null) {
                evictArticle(articleId);
                return null;
            }
            upsertCardHash(article, false);
            values = stringRedisTemplate.opsForHash().entries(cardHashKey(articleId));
        }
        return toSummaryVO(values);
    }

    private void flushDirtyArticlesToMysql() {
        while (true) {
            String articleIdText = stringRedisTemplate.opsForSet().pop(dirtyArticleSetKey());
            if (!StringUtils.hasText(articleIdText)) {
                return;
            }
            Long articleId = parseLong(articleIdText);
            if (articleId == null) {
                continue;
            }
            try {
                flushSingleDirtyArticle(articleId);
            } catch (Exception ex) {
                log.warn("flush dirty article cache failed, articleId={}", articleId, ex);
                stringRedisTemplate.opsForSet().add(dirtyArticleSetKey(), articleIdText);
            }
        }
    }

    private void flushSingleDirtyArticle(Long articleId) {
        String hashKey = cardHashKey(articleId);
        Map<Object, Object> values = stringRedisTemplate.opsForHash().entries(hashKey);
        if (values == null || values.isEmpty()) {
            return;
        }

        long viewCount = readLong(values, FIELD_VIEW_COUNT, 0L);
        long likeCount = readLong(values, FIELD_LIKE_COUNT, 0L);
        long favoriteCount = readLong(values, FIELD_FAVORITE_COUNT, 0L);

        articleMapper.ensureStatsByArticleId(articleId, 0L, 0L, 0L);
        articleMapper.updateViewCountById(articleId, viewCount);
        articleMapper.updateLikeCountById(articleId, likeCount);
        articleMapper.updateFavoriteCountById(articleId, favoriteCount);

        flushLikeRelationsIfInitialized(articleId);
        flushFavoriteRelationsIfInitialized(articleId);
    }

    private void flushLikeRelationsIfInitialized(Long articleId) {
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(likeInitKey(articleId)))) {
            return;
        }
        List<Long> userIds = readUserIdsFromSet(likeUserSetKey(articleId));
        articleLikeMapper.cancelAllActiveByArticleId(articleId, RELATION_CANCELLED, RELATION_ACTIVE);
        if (userIds.isEmpty()) {
            return;
        }
        for (List<Long> chunk : splitChunks(userIds)) {
            articleLikeMapper.batchUpsertStatusByArticleId(articleId, chunk, RELATION_ACTIVE);
        }
    }

    private void flushFavoriteRelationsIfInitialized(Long articleId) {
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(favoriteInitKey(articleId)))) {
            return;
        }
        List<Long> userIds = readUserIdsFromSet(favoriteUserSetKey(articleId));
        articleFavoriteMapper.cancelAllActiveByArticleId(articleId, RELATION_CANCELLED, RELATION_ACTIVE);
        if (userIds.isEmpty()) {
            return;
        }
        for (List<Long> chunk : splitChunks(userIds)) {
            articleFavoriteMapper.batchUpsertStatusByArticleId(articleId, chunk, RELATION_ACTIVE);
        }
    }

    private void rebuildFeedBaselineFromMysql() {
        List<ArticleEntity> latestArticles = articleMapper.selectPublishedPage(
                null,
                0,
                cacheProperties.getMaxItems(),
                SORT_BY_PUBLISHED_AT,
                "desc"
        );
        List<ArticleEntity> rankingArticles = articleMapper.selectPublishedPage(
                null,
                0,
                cacheProperties.getMaxItems(),
                SORT_BY_VIEW_COUNT,
                "desc"
        );

        String latestTmpKey = latestKey() + ":tmp";
        String rankingTmpKey = rankingKey() + ":tmp";
        stringRedisTemplate.delete(latestTmpKey);
        stringRedisTemplate.delete(rankingTmpKey);

        latestArticles.forEach(article -> stringRedisTemplate.opsForZSet().add(
                latestTmpKey,
                String.valueOf(article.getId()),
                publishedAtScore(article.getPublishedAt())
        ));
        rankingArticles.forEach(article -> stringRedisTemplate.opsForZSet().add(
                rankingTmpKey,
                String.valueOf(article.getId()),
                toDouble(article.getViewCount())
        ));

        Set<String> dirtyArticles = stringRedisTemplate.opsForSet().members(dirtyArticleSetKey());
        Map<Long, ArticleEntity> unionArticles = new LinkedHashMap<>();
        latestArticles.forEach(article -> unionArticles.put(article.getId(), article));
        rankingArticles.forEach(article -> unionArticles.put(article.getId(), article));

        unionArticles.values().forEach(article ->
                upsertCardHash(article, dirtyArticles != null && dirtyArticles.contains(String.valueOf(article.getId())))
        );

        if (latestArticles.isEmpty()) {
            stringRedisTemplate.delete(latestKey());
        } else {
            stringRedisTemplate.rename(latestTmpKey, latestKey());
        }
        if (rankingArticles.isEmpty()) {
            stringRedisTemplate.delete(rankingKey());
        } else {
            stringRedisTemplate.rename(rankingTmpKey, rankingKey());
        }
    }

    private void replayDirtyArticlesToFeeds() {
        Set<String> dirtyArticles = stringRedisTemplate.opsForSet().members(dirtyArticleSetKey());
        if (dirtyArticles == null || dirtyArticles.isEmpty()) {
            return;
        }
        for (String articleIdText : dirtyArticles) {
            Long articleId = parseLong(articleIdText);
            if (articleId == null) {
                continue;
            }
            ArticleEntity article = articleMapper.selectPublishedCardById(articleId);
            if (article == null) {
                evictArticle(articleId);
                continue;
            }
            upsertCardHash(article, true);
            promoteToFeeds(article);
        }
    }

    private void initializeLikeSetIfNeeded(Long articleId) {
        String initKey = likeInitKey(articleId);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(initKey))) {
            return;
        }
        String lockKey = initKey + ":lock";
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                lockKey,
                "1",
                Duration.ofSeconds(INIT_LOCK_SECONDS)
        );
        if (!Boolean.TRUE.equals(locked)) {
            waitForInitialization(initKey);
            return;
        }

        List<Long> userIds = articleLikeMapper.selectActiveUserIdsByArticleId(articleId, RELATION_ACTIVE);
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(initKey))) {
                return;
            }
            String setKey = likeUserSetKey(articleId);
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
        String initKey = favoriteInitKey(articleId);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(initKey))) {
            return;
        }
        String lockKey = initKey + ":lock";
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                lockKey,
                "1",
                Duration.ofSeconds(INIT_LOCK_SECONDS)
        );
        if (!Boolean.TRUE.equals(locked)) {
            waitForInitialization(initKey);
            return;
        }

        List<Long> userIds = articleFavoriteMapper.selectActiveUserIdsByArticleId(articleId, RELATION_ACTIVE);
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(initKey))) {
                return;
            }
            String setKey = favoriteUserSetKey(articleId);
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
                List.of(relationSetKey, hashKey, dirtyArticleSetKey()),
                userIdText,
                articleIdText,
                countField
        );
        return result == null ? Collections.emptyList() : result;
    }

    private void upsertCardHash(ArticleEntity article, boolean preserveHotCounts) {
        String hashKey = cardHashKey(article.getId());
        Map<Object, Object> existing = preserveHotCounts
                ? stringRedisTemplate.opsForHash().entries(hashKey)
                : Collections.emptyMap();

        Map<String, String> fields = new HashMap<>();
        fields.put(FIELD_ARTICLE_ID, String.valueOf(article.getId()));
        fields.put(FIELD_USER_ID, String.valueOf(article.getUserId()));
        fields.put(FIELD_TITLE, safeText(article.getTitle()));
        fields.put(FIELD_SUMMARY, safeText(article.getSummary()));
        fields.put(FIELD_COVER_URL, safeText(article.getCoverUrl()));
        fields.put(FIELD_PUBLISHED_AT, formatDateTime(article.getPublishedAt()));
        fields.put(FIELD_UPDATED_AT, formatDateTime(article.getUpdatedAt()));
        fields.put(FIELD_VIEW_COUNT, String.valueOf(resolvePreservedCount(existing, FIELD_VIEW_COUNT, article.getViewCount())));
        fields.put(FIELD_LIKE_COUNT, String.valueOf(resolvePreservedCount(existing, FIELD_LIKE_COUNT, article.getLikeCount())));
        fields.put(FIELD_FAVORITE_COUNT, String.valueOf(resolvePreservedCount(existing, FIELD_FAVORITE_COUNT, article.getFavoriteCount())));
        stringRedisTemplate.opsForHash().putAll(hashKey, fields);
    }

    private long resolveCurrentViewCount(ArticleEntity article) {
        Map<Object, Object> values = stringRedisTemplate.opsForHash().entries(cardHashKey(article.getId()));
        return readLong(values, FIELD_VIEW_COUNT, article.getViewCount());
    }

    private long resolvePreservedCount(Map<Object, Object> existing, String field, Long mysqlValue) {
        return readLong(existing, field, mysqlValue == null ? 0L : mysqlValue);
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

    private ArticleSummaryVO toSummaryVO(Map<Object, Object> values) {
        return ArticleSummaryVO.builder()
                .articleId(readNullableLong(values, FIELD_ARTICLE_ID, null))
                .userId(readNullableLong(values, FIELD_USER_ID, null))
                .title(readText(values, FIELD_TITLE, null))
                .summary(readText(values, FIELD_SUMMARY, null))
                .coverUrl(readText(values, FIELD_COVER_URL, null))
                .viewCount(readLong(values, FIELD_VIEW_COUNT, 0L))
                .likeCount(readLong(values, FIELD_LIKE_COUNT, 0L))
                .favoriteCount(readLong(values, FIELD_FAVORITE_COUNT, 0L))
                .liked(false)
                .favorited(false)
                .publishedAt(readDateTime(values, FIELD_PUBLISHED_AT, null))
                .updatedAt(readDateTime(values, FIELD_UPDATED_AT, null))
                .build();
    }

    private long readLong(Map<Object, Object> values, String field, Long defaultValue) {
        if (values == null || values.isEmpty()) {
            return defaultValue == null ? 0L : defaultValue;
        }
        Object value = values.get(field);
        Long parsed = value == null ? null : parseLong(String.valueOf(value));
        if (parsed == null) {
            return defaultValue == null ? 0L : defaultValue;
        }
        return parsed;
    }

    private Long readNullableLong(Map<Object, Object> values, String field, Long defaultValue) {
        if (values == null || values.isEmpty()) {
            return defaultValue;
        }
        Object value = values.get(field);
        Long parsed = value == null ? null : parseLong(String.valueOf(value));
        return parsed == null ? defaultValue : parsed;
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

    private String readText(Map<Object, Object> values, String field, String defaultValue) {
        if (values == null || values.isEmpty()) {
            return defaultValue;
        }
        Object value = values.get(field);
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value);
        return text.isEmpty() ? defaultValue : text;
    }

    private LocalDateTime readDateTime(Map<Object, Object> values, String field, LocalDateTime defaultValue) {
        if (values == null || values.isEmpty()) {
            return defaultValue;
        }
        Object value = values.get(field);
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return defaultValue;
        }
        try {
            return LocalDateTime.parse(String.valueOf(value));
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }

    private double publishedAtScore(LocalDateTime publishedAt) {
        if (publishedAt == null) {
            return 0D;
        }
        return publishedAt.atZone(ZONE_ID).toInstant().toEpochMilli();
    }

    private double toDouble(Long value) {
        return value == null ? 0D : value.doubleValue();
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

    private String feedKey(String sortBy) {
        return SORT_BY_VIEW_COUNT.equals(sortBy) ? rankingKey() : latestKey();
    }

    private String latestKey() {
        return cacheProperties.getLatestZsetKey();
    }

    private String rankingKey() {
        return cacheProperties.getRankingZsetKey();
    }

    private String cardHashKey(Long articleId) {
        return cacheProperties.getCardHashKeyPrefix() + articleId;
    }

    private String likeUserSetKey(Long articleId) {
        return cacheProperties.getLikeUserSetKeyPrefix() + articleId;
    }

    private String favoriteUserSetKey(Long articleId) {
        return cacheProperties.getFavoriteUserSetKeyPrefix() + articleId;
    }

    private String likeInitKey(Long articleId) {
        return cacheProperties.getLikeInitKeyPrefix() + articleId;
    }

    private String favoriteInitKey(Long articleId) {
        return cacheProperties.getFavoriteInitKeyPrefix() + articleId;
    }

    private String dirtyArticleSetKey() {
        return cacheProperties.getDirtyArticleSetKey();
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

    private long asLong(List<?> values, int index) {
        if (values == null || values.size() <= index) {
            return 0L;
        }
        Object value = values.get(index);
        if (value instanceof Number number) {
            return number.longValue();
        }
        Long parsed = value == null ? null : parseLong(String.valueOf(value));
        return parsed == null ? 0L : parsed;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
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
                redis.call('SADD', dirtySetKey, articleId)
                return {active, count}
                """);
        return script;
    }
}
