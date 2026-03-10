package org.example.backend.service.core.comment;

import org.example.backend.model.dto.comment.CreateCommentDTO;
import org.example.backend.model.vo.ArticleCommentVO;
import org.springframework.data.domain.Page;

public interface CommentService {

    Long createComment(Long userId, CreateCommentDTO dto);

    void deleteComment(Long userId, Long commentId);

    Page<ArticleCommentVO> listRootComments(Long viewerUserId, Long articleId, int page, int size);

    Page<ArticleCommentVO> listThreadComments(Long viewerUserId, Long articleId, Long rootId, int page, int size);
}
