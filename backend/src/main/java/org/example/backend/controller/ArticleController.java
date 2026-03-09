package org.example.backend.controller;

import lombok.RequiredArgsConstructor;
import org.example.backend.common.response.PageResult;
import org.example.backend.common.response.Result;
import org.example.backend.model.vo.ArticleDetailVO;
import org.example.backend.model.vo.ArticleSummaryVO;
import org.example.backend.service.core.article.ArticleService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/article")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @GetMapping("/{articleId}")
    public Result<ArticleDetailVO> getArticle(@PathVariable("articleId") Long articleId) {
        ArticleDetailVO article = articleService.getPublishedArticle(articleId);
        return Result.success(article);
    }

    @GetMapping
    public Result<PageResult<ArticleSummaryVO>> listArticles(@RequestParam(value = "userId", required = false) Long userId,
                                                             @RequestParam(value = "page", defaultValue = "0") int page,
                                                             @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<ArticleSummaryVO> pageData = articleService.listPublishedArticles(userId, page, size);
        return Result.success(PageResult.from(pageData));
    }
}
