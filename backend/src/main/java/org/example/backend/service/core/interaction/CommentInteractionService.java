package org.example.backend.service.core.interaction;

public interface CommentInteractionService {

    boolean toggleLikeComment(Long userId, Long commentId);
}
