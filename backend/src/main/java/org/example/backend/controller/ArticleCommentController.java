package org.example.backend.controller;

import lombok.RequiredArgsConstructor;
import org.example.backend.common.response.PageResult;
import org.example.backend.common.response.Result;
import org.example.backend.config.LoginUserPrincipal;
import org.example.backend.model.vo.ArticleCommentVO;
import org.example.backend.service.core.comment.CommentService;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/article/{articleId}/comment")
@RequiredArgsConstructor
public class ArticleCommentController {

    private final CommentService commentService;

    @GetMapping
    public Result<PageResult<ArticleCommentVO>> listRootComments(@PathVariable("articleId") Long articleId,
                                                                 @RequestParam(value = "page", defaultValue = "0") int page,
                                                                 @RequestParam(value = "size", defaultValue = "20") int size,
                                                                 Authentication authentication) {
        Long viewerUserId = tryGetViewerUserId(authentication);
        Page<ArticleCommentVO> pageData = commentService.listRootComments(viewerUserId, articleId, page, size);
        return Result.success(PageResult.from(pageData));
    }

    @GetMapping("/root/{rootId}")
    public Result<PageResult<ArticleCommentVO>> listThreadComments(@PathVariable("articleId") Long articleId,
                                                                   @PathVariable("rootId") Long rootId,
                                                                   @RequestParam(value = "page", defaultValue = "0") int page,
                                                                   @RequestParam(value = "size", defaultValue = "50") int size,
                                                                   Authentication authentication) {
        Long viewerUserId = tryGetViewerUserId(authentication);
        Page<ArticleCommentVO> pageData = commentService.listThreadComments(viewerUserId, articleId, rootId, page, size);
        return Result.success(PageResult.from(pageData));
    }

    private Long tryGetViewerUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUserPrincipal principal)) {
            return null;
        }
        return principal.getUserId();
    }
}
