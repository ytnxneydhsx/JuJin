package org.example.backend.service.core.article;

public interface ArticleViewCountService {

    long increaseAndGet(Long articleId, Long mysqlBaseCount);
}
