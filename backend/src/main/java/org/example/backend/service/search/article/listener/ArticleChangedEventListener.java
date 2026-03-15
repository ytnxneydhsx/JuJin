package org.example.backend.service.search.article.listener;

import lombok.RequiredArgsConstructor;
import org.example.backend.event.article.ArticleChangedEvent;
import org.example.backend.service.search.article.ArticleSearchIndexService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ArticleChangedEventListener {

    private final ArticleSearchIndexService articleSearchIndexService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onArticleChanged(ArticleChangedEvent event) {
        articleSearchIndexService.syncByArticleId(event.articleId());
    }
}
