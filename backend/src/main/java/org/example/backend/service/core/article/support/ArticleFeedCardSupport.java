package org.example.backend.service.core.article.support;

import lombok.RequiredArgsConstructor;
import org.example.backend.mapper.article.ArticleMapper;
import org.example.backend.model.entity.ArticleEntity;
import org.example.backend.model.vo.ArticleDetailVO;
import org.example.backend.model.vo.ArticleSummaryVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ArticleFeedCardSupport {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final StringRedisTemplate stringRedisTemplate;
    private final ArticleMapper articleMapper;
    private final ArticleFeedCacheKeys articleFeedCacheKeys;
    private final ArticleFeedCardCodec articleFeedCardCodec;

    public void applyCard(ArticleDetailVO detail) {
        if (detail == null || detail.getArticleId() == null) {
            return;
        }
        Map<Object, Object> values = stringRedisTemplate.opsForHash()
                .entries(articleFeedCacheKeys.cardHashKey(detail.getArticleId()));
        articleFeedCardCodec.applyCard(detail, values);
    }

    public void applyCard(List<ArticleSummaryVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        records.forEach(this::applyCard);
    }

    public void ensureCardHash(ArticleEntity article) {
        String hashKey = articleFeedCacheKeys.cardHashKey(article.getId());
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(hashKey))) {
            return;
        }
        upsertCardHash(article);
    }

    public void upsertCardHash(ArticleEntity article) {
        String hashKey = articleFeedCacheKeys.cardHashKey(article.getId());
        stringRedisTemplate.opsForHash().putAll(hashKey, articleFeedCardCodec.buildCardFields(article));
    }

    public void promoteToFeeds(ArticleEntity article, long currentViewCount) {
        String articleIdText = String.valueOf(article.getId());
        stringRedisTemplate.opsForZSet().add(
                articleFeedCacheKeys.latestKey(),
                articleIdText,
                publishedAtScore(article.getPublishedAt())
        );
        stringRedisTemplate.opsForZSet().add(
                articleFeedCacheKeys.rankingKey(),
                articleIdText,
                longScore(currentViewCount)
        );
    }

    public void evictArticle(Long articleId) {
        String articleIdText = String.valueOf(articleId);
        stringRedisTemplate.opsForZSet().remove(articleFeedCacheKeys.latestKey(), articleIdText);
        stringRedisTemplate.opsForZSet().remove(articleFeedCacheKeys.rankingKey(), articleIdText);
        stringRedisTemplate.delete(articleFeedCacheKeys.cardHashKey(articleId));
    }

    public ArticleSummaryVO getOrLoadSummary(Long articleId) {
        Map<Object, Object> values = stringRedisTemplate.opsForHash().entries(articleFeedCacheKeys.cardHashKey(articleId));
        if (values == null || values.isEmpty()) {
            ArticleEntity article = articleMapper.selectPublishedCardById(articleId);
            if (article == null) {
                evictArticle(articleId);
                return null;
            }
            upsertCardHash(article);
            return ArticleSummaryVO.builder()
                    .articleId(article.getId())
                    .userId(article.getUserId())
                    .title(article.getTitle())
                    .summary(article.getSummary())
                    .coverUrl(article.getCoverUrl())
                    .viewCount(article.getViewCount())
                    .likeCount(article.getLikeCount())
                    .favoriteCount(article.getFavoriteCount())
                    .liked(false)
                    .favorited(false)
                    .publishedAt(article.getPublishedAt())
                    .updatedAt(article.getUpdatedAt())
                    .build();
        }
        return articleFeedCardCodec.toSummaryVO(values);
    }

    public double publishedAtScore(LocalDateTime publishedAt) {
        if (publishedAt == null) {
            return 0D;
        }
        return publishedAt.atZone(ZONE_ID).toInstant().toEpochMilli();
    }

    public double longScore(Long value) {
        return value == null ? 0D : value.doubleValue();
    }

    private void applyCard(ArticleSummaryVO summary) {
        if (summary == null || summary.getArticleId() == null) {
            return;
        }
        Map<Object, Object> values = stringRedisTemplate.opsForHash()
                .entries(articleFeedCacheKeys.cardHashKey(summary.getArticleId()));
        articleFeedCardCodec.applyCard(summary, values);
    }
}
