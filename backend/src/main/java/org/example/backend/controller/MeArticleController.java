package org.example.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.backend.common.response.PageResult;
import org.example.backend.common.response.Result;
import org.example.backend.config.LoginUserPrincipal;
import org.example.backend.exception.BizException;
import org.example.backend.model.dto.article.SaveDraftDTO;
import org.example.backend.model.dto.article.UpdateArticleDTO;
import org.example.backend.model.vo.ArticleDetailVO;
import org.example.backend.model.vo.ArticleDraftVO;
import org.example.backend.model.vo.ArticleIdVO;
import org.example.backend.model.vo.ArticleSummaryVO;
import org.example.backend.model.vo.DraftIdVO;
import org.example.backend.service.article.ArticleService;
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

    @PostMapping("/draft")
    public Result<DraftIdVO> createDraft(@Valid @RequestBody SaveDraftDTO dto, Authentication authentication) {
        Long userId = requireLoginUserId(authentication);
        Long draftId = articleService.createDraft(userId, dto);
        return Result.success("Draft created successfully", DraftIdVO.builder().draftId(draftId).build());
    }

    @PutMapping("/draft/{draftId}")
    public Result<Void> updateDraft(@PathVariable("draftId") Long draftId,
                                    @Valid @RequestBody SaveDraftDTO dto,
                                    Authentication authentication) {
        Long userId = requireLoginUserId(authentication);
        articleService.updateDraft(userId, draftId, dto);
        return Result.success("Draft updated successfully", null);
    }

    @GetMapping("/draft/{draftId}")
    public Result<ArticleDraftVO> getDraft(@PathVariable("draftId") Long draftId,
                                           Authentication authentication) {
        Long userId = requireLoginUserId(authentication);
        ArticleDraftVO draft = articleService.getDraft(userId, draftId);
        return Result.success(draft);
    }

    @GetMapping("/drafts")
    public Result<PageResult<ArticleDraftVO>> listDrafts(@RequestParam(value = "page", defaultValue = "0") int page,
                                                         @RequestParam(value = "size", defaultValue = "20") int size,
                                                         Authentication authentication) {
        Long userId = requireLoginUserId(authentication);
        Page<ArticleDraftVO> pageData = articleService.listDrafts(userId, page, size);
        return Result.success(PageResult.from(pageData));
    }

    @PostMapping("/draft/{draftId}/publish")
    public Result<ArticleIdVO> publishDraft(@PathVariable("draftId") Long draftId,
                                            Authentication authentication) {
        Long userId = requireLoginUserId(authentication);
        Long articleId = articleService.publishDraft(userId, draftId);
        return Result.success("Article published successfully", ArticleIdVO.builder().articleId(articleId).build());
    }

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

    @GetMapping("/{articleId}")
    public Result<ArticleDetailVO> getMyArticle(@PathVariable("articleId") Long articleId,
                                                Authentication authentication) {
        Long userId = requireLoginUserId(authentication);
        ArticleDetailVO article = articleService.getMyArticle(userId, articleId);
        return Result.success(article);
    }

    @GetMapping
    public Result<PageResult<ArticleSummaryVO>> listMyArticles(@RequestParam(value = "page", defaultValue = "0") int page,
                                                               @RequestParam(value = "size", defaultValue = "20") int size,
                                                               Authentication authentication) {
        Long userId = requireLoginUserId(authentication);
        Page<ArticleSummaryVO> pageData = articleService.listMyArticles(userId, page, size);
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
