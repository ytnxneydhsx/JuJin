package org.example.backend.service.core.article.support;

import org.example.backend.model.entity.ArticleEntity;
import org.example.backend.model.vo.ArticleDetailVO;
import org.example.backend.model.vo.ArticleSummaryVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class ArticleFeedCardCodec {

    public Map<String, String> buildCardFields(ArticleEntity article) {
        Map<String, String> fields = new HashMap<>();
        fields.put(ArticleFeedCardFields.ARTICLE_ID, String.valueOf(article.getId()));
        fields.put(ArticleFeedCardFields.USER_ID, String.valueOf(article.getUserId()));
        fields.put(ArticleFeedCardFields.TITLE, safeText(article.getTitle()));
        fields.put(ArticleFeedCardFields.SUMMARY, safeText(article.getSummary()));
        fields.put(ArticleFeedCardFields.COVER_URL, safeText(article.getCoverUrl()));
        fields.put(ArticleFeedCardFields.PUBLISHED_AT, formatDateTime(article.getPublishedAt()));
        fields.put(ArticleFeedCardFields.UPDATED_AT, formatDateTime(article.getUpdatedAt()));
        return fields;
    }

    public void applyCard(ArticleDetailVO detail, Map<Object, Object> values) {
        if (detail == null || values == null || values.isEmpty()) {
            return;
        }
        detail.setSummary(readText(values, ArticleFeedCardFields.SUMMARY, detail.getSummary()));
        detail.setCoverUrl(readText(values, ArticleFeedCardFields.COVER_URL, detail.getCoverUrl()));
        detail.setPublishedAt(readDateTime(values, ArticleFeedCardFields.PUBLISHED_AT, detail.getPublishedAt()));
        detail.setUpdatedAt(readDateTime(values, ArticleFeedCardFields.UPDATED_AT, detail.getUpdatedAt()));
    }

    public void applyCard(ArticleSummaryVO summary, Map<Object, Object> values) {
        if (summary == null || values == null || values.isEmpty()) {
            return;
        }
        summary.setSummary(readText(values, ArticleFeedCardFields.SUMMARY, summary.getSummary()));
        summary.setCoverUrl(readText(values, ArticleFeedCardFields.COVER_URL, summary.getCoverUrl()));
        summary.setPublishedAt(readDateTime(values, ArticleFeedCardFields.PUBLISHED_AT, summary.getPublishedAt()));
        summary.setUpdatedAt(readDateTime(values, ArticleFeedCardFields.UPDATED_AT, summary.getUpdatedAt()));
    }

    public ArticleSummaryVO toSummaryVO(Map<Object, Object> values) {
        return ArticleSummaryVO.builder()
                .articleId(readNullableLong(values, ArticleFeedCardFields.ARTICLE_ID, null))
                .userId(readNullableLong(values, ArticleFeedCardFields.USER_ID, null))
                .title(readText(values, ArticleFeedCardFields.TITLE, null))
                .summary(readText(values, ArticleFeedCardFields.SUMMARY, null))
                .coverUrl(readText(values, ArticleFeedCardFields.COVER_URL, null))
                .viewCount(0L)
                .likeCount(0L)
                .favoriteCount(0L)
                .liked(false)
                .favorited(false)
                .publishedAt(readDateTime(values, ArticleFeedCardFields.PUBLISHED_AT, null))
                .updatedAt(readDateTime(values, ArticleFeedCardFields.UPDATED_AT, null))
                .build();
    }

    public Long readNullableLong(Map<Object, Object> values, String field, Long defaultValue) {
        if (values == null || values.isEmpty()) {
            return defaultValue;
        }
        Object value = values.get(field);
        Long parsed = parseLong(value);
        return parsed == null ? defaultValue : parsed;
    }

    public String readText(Map<Object, Object> values, String field, String defaultValue) {
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

    public LocalDateTime readDateTime(Map<Object, Object> values, String field, LocalDateTime defaultValue) {
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

    private String safeText(String value) {
        return value == null ? "" : value;
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
