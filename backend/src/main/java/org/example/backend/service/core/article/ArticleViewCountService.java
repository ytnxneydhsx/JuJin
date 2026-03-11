package org.example.backend.service.core.article;

public interface ArticleViewCountService {

    long increaseAndGet(Long articleId, Long mysqlBaseCount);

    long getViewCount(Long articleId, Long mysqlBaseCount);
}
