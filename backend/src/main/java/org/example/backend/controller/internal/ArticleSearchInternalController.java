package org.example.backend.controller.internal;

import lombok.RequiredArgsConstructor;
import org.example.backend.common.response.Result;
import org.example.backend.model.vo.ArticleSearchRebuildVO;
import org.example.backend.model.vo.ArticleSearchSyncVO;
import org.example.backend.service.search.article.ArticleSearchService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/search/articles")
@RequiredArgsConstructor
public class ArticleSearchInternalController {

    private final ArticleSearchService articleSearchService;

    @PostMapping("/rebuild-index")
    public Result<ArticleSearchRebuildVO> rebuildIndex() {
        long indexedCount = articleSearchService.rebuildIndex();
        return Result.success("Article index rebuilt successfully",
                ArticleSearchRebuildVO.builder().indexedCount(indexedCount).build());
    }

    @PostMapping("/sync/{articleId}")
    public Result<ArticleSearchSyncVO> syncByArticleId(@PathVariable("articleId") Long articleId) {
        articleSearchService.syncByArticleId(articleId);
        return Result.success("Article index synced successfully",
                ArticleSearchSyncVO.builder().articleId(articleId).build());
    }
}
