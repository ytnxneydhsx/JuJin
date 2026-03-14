package org.example.backend.service.core.article.support;

import lombok.RequiredArgsConstructor;
import org.example.backend.common.constant.AppConstants.ArticleSort;
import org.example.backend.config.AppArticleFeedCacheProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ArticleFeedCacheKeys {

    private static final String TEMP_SUFFIX = ":tmp";
    private static final String LOCK_SUFFIX = ":lock";

    private final AppArticleFeedCacheProperties cacheProperties;

    public String feedKey(String sortBy) {
        return ArticleSort.BY_VIEW_COUNT.equals(sortBy) ? rankingKey() : latestKey();
    }

    public String latestKey() {
        return cacheProperties.getLatestZsetKey();
    }

    public String latestTempKey() {
        return latestKey() + TEMP_SUFFIX;
    }

    public String rankingKey() {
        return cacheProperties.getRankingZsetKey();
    }

    public String rankingTempKey() {
        return rankingKey() + TEMP_SUFFIX;
    }

    public String cardHashKey(Long articleId) {
        return cacheProperties.getCardHashKeyPrefix() + articleId;
    }

    public String statsHashKey(Long articleId) {
        return cacheProperties.getStatsHashKeyPrefix() + articleId;
    }

    public String likeUserSetKey(Long articleId) {
        return cacheProperties.getLikeUserSetKeyPrefix() + articleId;
    }

    public String favoriteUserSetKey(Long articleId) {
        return cacheProperties.getFavoriteUserSetKeyPrefix() + articleId;
    }

    public String likeInitKey(Long articleId) {
        return cacheProperties.getLikeInitKeyPrefix() + articleId;
    }

    public String likeInitLockKey(Long articleId) {
        return likeInitKey(articleId) + LOCK_SUFFIX;
    }

    public String favoriteInitKey(Long articleId) {
        return cacheProperties.getFavoriteInitKeyPrefix() + articleId;
    }

    public String favoriteInitLockKey(Long articleId) {
        return favoriteInitKey(articleId) + LOCK_SUFFIX;
    }

    public String dirtyArticleSetKey() {
        return cacheProperties.getDirtyArticleSetKey();
    }

    public String interactionActiveKey() {
        return cacheProperties.getInteractionActiveZsetKey();
    }
}
