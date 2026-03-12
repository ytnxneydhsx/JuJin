package org.example.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.backend.common.response.PageResult;
import org.example.backend.common.response.Result;
import org.example.backend.config.LoginUserPrincipal;
import org.example.backend.exception.BizException;
import org.example.backend.model.dto.article.UpdateArticleDTO;
import org.example.backend.model.vo.ArticleDetailVO;
import org.example.backend.model.vo.ArticleFavoriteVO;
import org.example.backend.model.vo.ArticleLikeVO;
import org.example.backend.model.vo.ArticleSummaryVO;
import org.example.backend.service.core.article.ArticleQueryService;
import org.example.backend.service.core.article.ArticleService;
import org.example.backend.service.core.interaction.ArticleInteractionService;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/article")
@RequiredArgsConstructor
public class MeArticleController {

    private final ArticleService articleService;
    private final ArticleQueryService articleQueryService;
    private final ArticleInteractionService articleInteractionService;

    @PutMapping("/{articleId}")
    public Result<Void> updateArticle(@PathVariable("articleId") Long articleId,
                                      @Valid @RequestBody UpdateArticleDTO dto,
                                      Authentication authentication) {
        Long userId = requireLoginUserId(authentication);
        articleService.updateArticle(userId, articleId, dto);
        return Result.success("Article updated successfully", null);
    }

    @DeleteMapping("/{articleId}")
    public Result<Void> deleteArticle(@PathVariable("articleId") Long articleId,
                                      Authentication authentication) {
        Long userId = requireLoginUserId(authentication);
        articleService.deleteArticle(userId, articleId);
        return Result.success("Article deleted successfully", null);
    }

    @PostMapping("/{articleId}/like")
    public Result<ArticleLikeVO> likeArticle(@PathVariable("articleId") Long articleId,
                                             Authentication authentication) {
        Long userId = requireLoginUserId(authentication);
        ArticleLikeVO result = articleInteractionService.likeArticle(userId, articleId);
        return Result.success("Article like toggled successfully", result);
    }

    @PostMapping("/{articleId}/favorite")
    public Result<ArticleFavoriteVO> favoriteArticle(@PathVariable("articleId") Long articleId,
                                                     Authentication authentication) {
        Long userId = requireLoginUserId(authentication);
        ArticleFavoriteVO result = articleInteractionService.favoriteArticle(userId, articleId);
        return Result.success("Article favorite toggled successfully", result);
    }

    @GetMapping("/{articleId}")
    public Result<ArticleDetailVO> getMyArticle(@PathVariable("articleId") Long articleId,
                                                Authentication authentication) {
        Long userId = requireLoginUserId(authentication);
        ArticleDetailVO article = articleQueryService.getMyArticle(userId, articleId);
        return Result.success(article);
    }

    @GetMapping
    public Result<PageResult<ArticleSummaryVO>> listMyArticles(@RequestParam(value = "page", defaultValue = "0") int page,
                                                               @RequestParam(value = "size", defaultValue = "20") int size,
                                                               Authentication authentication) {
        Long userId = requireLoginUserId(authentication);
        Page<ArticleSummaryVO> pageData = articleQueryService.listMyArticles(userId, page, size);
        return Result.success(PageResult.from(pageData));
    }

    private Long requireLoginUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUserPrincipal principal)) {
            throw new BizException("UNAUTHORIZED", "Please login first");
        }
        if (principal.getUserId() == null) {
            throw new BizException("UNAUTHORIZED", "Please login first");
        }
        return principal.getUserId();
    }
}
