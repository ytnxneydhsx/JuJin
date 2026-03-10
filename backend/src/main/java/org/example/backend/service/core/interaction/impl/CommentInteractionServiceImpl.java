package org.example.backend.service.core.interaction.impl;

import lombok.RequiredArgsConstructor;
import org.example.backend.exception.BizException;
import org.example.backend.mapper.comment.ArticleCommentMapper;
import org.example.backend.mapper.interaction.CommentLikeMapper;
import org.example.backend.service.core.interaction.CommentInteractionService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentInteractionServiceImpl implements CommentInteractionService {

    private static final int COMMENT_STATUS_NORMAL = 1;
    private static final int RELATION_ACTIVE = 1;
    private static final int RELATION_CANCELLED = 0;

    private final ArticleCommentMapper articleCommentMapper;
    private final CommentLikeMapper commentLikeMapper;

    @Override
    @Transactional
    public boolean toggleLikeComment(Long userId, Long commentId) {
        validatePositive("userId", userId);
        validatePositive("commentId", commentId);
        ensureCommentExists(commentId);

        try {
            commentLikeMapper.insert(userId, commentId, RELATION_ACTIVE);
            return true;
        } catch (DuplicateKeyException ex) {
            int toCancelled = commentLikeMapper.updateStatusByUserIdAndCommentId(
                    userId,
                    commentId,
                    RELATION_CANCELLED,
                    RELATION_ACTIVE
            );
            if (toCancelled == 1) {
                return false;
            }

            int toActive = commentLikeMapper.updateStatusByUserIdAndCommentId(
                    userId,
                    commentId,
                    RELATION_ACTIVE,
                    RELATION_CANCELLED
            );
            if (toActive == 1) {
                return true;
            }

            throw new BizException("COMMENT_LIKE_FAILED", "Failed to toggle comment like status");
        }
    }

    private void ensureCommentExists(Long commentId) {
        if (articleCommentMapper.countByIdAndStatus(commentId, COMMENT_STATUS_NORMAL) != 1) {
            throw new BizException("COMMENT_NOT_FOUND", "Comment not found");
        }
    }

    private void validatePositive(String fieldName, Long value) {
        if (value == null || value <= 0) {
            throw new BizException("INVALID_PARAM", fieldName + " must be a positive number");
        }
    }
}
