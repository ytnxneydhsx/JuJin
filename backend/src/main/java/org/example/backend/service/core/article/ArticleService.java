package org.example.backend.service.core.article;

import org.example.backend.model.dto.article.UpdateArticleDTO;
import org.example.backend.model.vo.ArticleDetailVO;
import org.example.backend.model.vo.ArticleSummaryVO;
import org.springframework.data.domain.Page;

public interface ArticleService {

    void updateArticle(Long userId, Long articleId, UpdateArticleDTO dto);

    void deleteArticle(Long userId, Long articleId);

    ArticleDetailVO getMyArticle(Long userId, Long articleId);

    Page<ArticleSummaryVO> listMyArticles(Long userId, int page, int size);

    ArticleDetailVO getPublishedArticle(Long viewerUserId, Long articleId);

    Page<ArticleSummaryVO> listPublishedArticles(Long viewerUserId,
                                                 Long authorUserId,
                                                 String sortBy,
                                                 String sortOrder,
                                                 int page,
                                                 int size);
}
