package org.example.backend.service.core.article.support;

import org.example.backend.model.entity.ArticleEntity;
import org.example.backend.model.vo.ArticleDetailVO;
import org.example.backend.model.vo.ArticleSummaryVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Component
public class ArticleFeedStatsCodec {

    public Map<String, String> buildStatsFields(ArticleEntity article) {
        Map<String, String> fields = new HashMap<>();
        fields.put(ArticleFeedStatsFields.VIEW_COUNT, String.valueOf(article.getViewCount() == null ? 0L : article.getViewCount()));
        fields.put(ArticleFeedStatsFields.LIKE_COUNT, String.valueOf(article.getLikeCount() == null ? 0L : article.getLikeCount()));
        fields.put(ArticleFeedStatsFields.FAVORITE_COUNT, String.valueOf(article.getFavoriteCount() == null ? 0L : article.getFavoriteCount()));
        return fields;
    }

    public void applyStats(ArticleDetailVO detail, Map<Object, Object> values) {
        if (detail == null || values == null || values.isEmpty()) {
            return;
        }
        detail.setViewCount(readLong(values, ArticleFeedStatsFields.VIEW_COUNT, detail.getViewCount()));
        detail.setLikeCount(readLong(values, ArticleFeedStatsFields.LIKE_COUNT, detail.getLikeCount()));
        detail.setFavoriteCount(readLong(values, ArticleFeedStatsFields.FAVORITE_COUNT, detail.getFavoriteCount()));
    }

    public void applyStats(ArticleSummaryVO summary, Map<Object, Object> values) {
        if (summary == null || values == null || values.isEmpty()) {
            return;
        }
        summary.setViewCount(readLong(values, ArticleFeedStatsFields.VIEW_COUNT, summary.getViewCount()));
        summary.setLikeCount(readLong(values, ArticleFeedStatsFields.LIKE_COUNT, summary.getLikeCount()));
        summary.setFavoriteCount(readLong(values, ArticleFeedStatsFields.FAVORITE_COUNT, summary.getFavoriteCount()));
    }

    public long readLong(Map<Object, Object> values, String field, Long defaultValue) {
        if (values == null || values.isEmpty()) {
            return defaultValue == null ? 0L : defaultValue;
        }
        Object value = values.get(field);
        Long parsed = parseLong(value);
        if (parsed == null) {
            return defaultValue == null ? 0L : defaultValue;
        }
        return parsed;
    }

    public Long readNullableLong(Map<Object, Object> values, String field, Long defaultValue) {
        if (values == null || values.isEmpty()) {
            return defaultValue;
        }
        Object value = values.get(field);
        Long parsed = parseLong(value);
        return parsed == null ? defaultValue : parsed;
    }

    private Long parseLong(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
