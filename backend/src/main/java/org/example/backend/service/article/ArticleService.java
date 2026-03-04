package org.example.backend.service.article;

import org.example.backend.model.dto.article.SaveDraftDTO;
import org.example.backend.model.dto.article.UpdateArticleDTO;
import org.example.backend.model.vo.ArticleDetailVO;
import org.example.backend.model.vo.ArticleDraftVO;
import org.example.backend.model.vo.ArticleSummaryVO;
import org.springframework.data.domain.Page;

public interface ArticleService {

    Long createDraft(Long userId, SaveDraftDTO dto);

    void updateDraft(Long userId, Long draftId, SaveDraftDTO dto);

    ArticleDraftVO getDraft(Long userId, Long draftId);

    Page<ArticleDraftVO> listDrafts(Long userId, int page, int size);

    Long publishDraft(Long userId, Long draftId);

    void updateArticle(Long userId, Long articleId, UpdateArticleDTO dto);

    void deleteArticle(Long userId, Long articleId);

    ArticleDetailVO getMyArticle(Long userId, Long articleId);

    Page<ArticleSummaryVO> listMyArticles(Long userId, int page, int size);

    ArticleDetailVO getPublishedArticle(Long articleId);

    Page<ArticleSummaryVO> listPublishedArticles(Long authorUserId, int page, int size);
}
