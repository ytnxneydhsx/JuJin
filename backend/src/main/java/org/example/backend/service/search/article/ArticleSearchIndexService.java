package org.example.backend.service.search.article;

public interface ArticleSearchIndexService {

    long rebuildIndex();

    void syncByArticleId(Long articleId);
}
