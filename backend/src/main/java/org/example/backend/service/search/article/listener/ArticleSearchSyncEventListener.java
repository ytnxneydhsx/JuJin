package org.example.backend.service.search.article.listener;

import lombok.RequiredArgsConstructor;
import org.example.backend.event.article.ArticleSearchSyncEvent;
import org.example.backend.service.search.article.ArticleSearchService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ArticleSearchSyncEventListener {

    private final ArticleSearchService articleSearchService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onArticleSearchSync(ArticleSearchSyncEvent event) {
        articleSearchService.syncByArticleId(event.articleId());
    }
}
