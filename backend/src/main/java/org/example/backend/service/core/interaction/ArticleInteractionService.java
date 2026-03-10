package org.example.backend.service.core.interaction;

import org.example.backend.model.vo.ArticleFavoriteVO;
import org.example.backend.model.vo.ArticleLikeVO;

public interface ArticleInteractionService {

    ArticleLikeVO likeArticle(Long userId, Long articleId);

    ArticleFavoriteVO favoriteArticle(Long userId, Long articleId);
}
