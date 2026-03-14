package org.example.backend.event.article;

public record ArticleChangedEvent(Long articleId) {

    public ArticleChangedEvent {
        if (articleId == null || articleId <= 0) {
            throw new IllegalArgumentException("articleId must be a positive number");
        }
    }
}
