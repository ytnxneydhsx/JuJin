package org.example.backend.service.core.interaction;

import org.example.backend.model.vo.ArticleLikeVO;

public interface ArticleLikeCacheService {

    ArticleLikeVO toggleLike(Long userId, Long articleId, Long mysqlBaseLikeCount);

    long getLikeCount(Long articleId, Long mysqlBaseLikeCount);

    boolean resolveLiked(Long userId, Long articleId, boolean mysqlFallbackLiked);
}
