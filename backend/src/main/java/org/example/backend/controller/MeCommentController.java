package org.example.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.backend.common.response.Result;
import org.example.backend.config.LoginUserPrincipal;
import org.example.backend.exception.BizException;
import org.example.backend.model.dto.comment.CreateCommentDTO;
import org.example.backend.model.vo.CommentIdVO;
import org.example.backend.model.vo.CommentLikeToggleVO;
import org.example.backend.service.core.comment.CommentService;
import org.example.backend.service.core.interaction.CommentInteractionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/comment")
@RequiredArgsConstructor
public class MeCommentController {

    private final CommentService commentService;
    private final CommentInteractionService commentInteractionService;

    @PostMapping
    public Result<CommentIdVO> createComment(@Valid @RequestBody CreateCommentDTO dto,
                                             Authentication authentication) {
        Long userId = requireLoginUserId(authentication);
        Long commentId = commentService.createComment(userId, dto);
        return Result.success("Comment created successfully", CommentIdVO.builder().commentId(commentId).build());
    }

    @DeleteMapping("/{commentId}")
    public Result<Void> deleteComment(@PathVariable("commentId") Long commentId,
                                      Authentication authentication) {
        Long userId = requireLoginUserId(authentication);
        commentService.deleteComment(userId, commentId);
        return Result.success("Comment deleted successfully", null);
    }

    @PostMapping("/{commentId}/like")
    public Result<CommentLikeToggleVO> toggleLikeComment(@PathVariable("commentId") Long commentId,
                                                         Authentication authentication) {
        Long userId = requireLoginUserId(authentication);
        boolean liked = commentInteractionService.toggleLikeComment(userId, commentId);
        return Result.success("Comment like toggled successfully",
                CommentLikeToggleVO.builder()
                        .commentId(commentId)
                        .liked(liked)
                        .build());
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
