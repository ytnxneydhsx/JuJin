package org.example.backend.service.core.article;

import org.example.backend.model.dto.article.UpdateArticleDTO;

public interface ArticleService {

    void updateArticle(Long userId, Long articleId, UpdateArticleDTO dto);

    void deleteArticle(Long userId, Long articleId);
}
