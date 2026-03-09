package org.example.backend.event.article;

public record ArticleSearchSyncEvent(Long articleId) {

    public ArticleSearchSyncEvent {
        if (articleId == null || articleId <= 0) {
            throw new IllegalArgumentException("articleId must be a positive number");
        }
    }
}
