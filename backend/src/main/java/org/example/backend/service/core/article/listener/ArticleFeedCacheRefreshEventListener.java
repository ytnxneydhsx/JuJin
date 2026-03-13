package org.example.backend.service.core.article.listener;

import lombok.RequiredArgsConstructor;
import org.example.backend.event.article.ArticleSearchSyncEvent;
import org.example.backend.service.core.article.ArticleFeedCacheService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ArticleFeedCacheRefreshEventListener {

    private final ArticleFeedCacheService articleFeedCacheService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onArticleSearchSync(ArticleSearchSyncEvent event) {
        articleFeedCacheService.refreshArticle(event.articleId());
    }
}
