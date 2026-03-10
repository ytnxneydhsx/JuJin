package org.example.backend.service.core.comment.impl;

import lombok.RequiredArgsConstructor;
import org.example.backend.exception.BizException;
import org.example.backend.mapper.article.ArticleMapper;
import org.example.backend.mapper.comment.ArticleCommentMapper;
import org.example.backend.mapper.interaction.CommentLikeMapper;
import org.example.backend.model.dto.comment.CreateCommentDTO;
import org.example.backend.model.entity.ArticleCommentEntity;
import org.example.backend.model.vo.ArticleCommentVO;
import org.example.backend.service.core.comment.CommentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private static final int COMMENT_STATUS_NORMAL = 1;
    private static final int COMMENT_STATUS_DELETED = 2;
    private static final int RELATION_ACTIVE = 1;

    private final ArticleMapper articleMapper;
    private final ArticleCommentMapper articleCommentMapper;
    private final CommentLikeMapper commentLikeMapper;

    @Override
    @Transactional
    public Long createComment(Long userId, CreateCommentDTO dto) {
        validatePositive("userId", userId);
        validatePositive("articleId", dto.getArticleId());

        String content = trimToNull(dto.getContent());
        if (!StringUtils.hasText(content)) {
            throw new BizException("INVALID_PARAM", "content cannot be blank");
        }
        ensurePublishedArticleExists(dto.getArticleId());

        Long parentId = dto.getParentId();
        if (parentId == null) {
            return createRootComment(userId, dto.getArticleId(), content);
        }
        validatePositive("parentId", parentId);
        return createReplyComment(userId, dto.getArticleId(), parentId, content);
    }

    @Override
    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        validatePositive("userId", userId);
        validatePositive("commentId", commentId);

        int affected = articleCommentMapper.updateStatusByIdAndUserId(
                commentId,
                userId,
                COMMENT_STATUS_DELETED,
                COMMENT_STATUS_NORMAL
        );
        if (affected != 1) {
            throw new BizException("COMMENT_NOT_FOUND", "Comment not found");
        }
    }

    @Override
    public Page<ArticleCommentVO> listRootComments(Long viewerUserId, Long articleId, int page, int size) {
        validatePositive("articleId", articleId);
        Pageable pageable = requirePageable(page, size);
        int offset = Math.toIntExact(pageable.getOffset());

        List<ArticleCommentVO> records = articleCommentMapper.selectRootPageByArticleId(
                        articleId,
                        COMMENT_STATUS_NORMAL,
                        offset,
                        pageable.getPageSize()
                )
                .stream()
                .map(this::toVO)
                .toList();
        applyLikedState(records, viewerUserId);
        long total = articleCommentMapper.countRootByArticleId(articleId, COMMENT_STATUS_NORMAL);
        return new PageImpl<>(records, pageable, total);
    }

    @Override
    public Page<ArticleCommentVO> listThreadComments(Long viewerUserId, Long articleId, Long rootId, int page, int size) {
        validatePositive("articleId", articleId);
        validatePositive("rootId", rootId);
        Pageable pageable = requirePageable(page, size);
        int offset = Math.toIntExact(pageable.getOffset());

        List<ArticleCommentVO> records = articleCommentMapper.selectThreadPageByArticleIdAndRootId(
                        articleId,
                        rootId,
                        COMMENT_STATUS_NORMAL,
                        offset,
                        pageable.getPageSize()
                )
                .stream()
                .map(this::toVO)
                .toList();
        applyLikedState(records, viewerUserId);
        long total = articleCommentMapper.countThreadByArticleIdAndRootId(articleId, rootId, COMMENT_STATUS_NORMAL);
        return new PageImpl<>(records, pageable, total);
    }

    private Long createRootComment(Long userId, Long articleId, String content) {
        ArticleCommentEntity entity = new ArticleCommentEntity();
        entity.setArticleId(articleId);
        entity.setUserId(userId);
        entity.setRootId(0L);
        entity.setParentId(null);
        entity.setReplyToUserId(null);
        entity.setContent(content);
        entity.setStatus(COMMENT_STATUS_NORMAL);

        int affected = articleCommentMapper.insert(entity);
        if (affected != 1 || entity.getId() == null) {
            throw new BizException("COMMENT_CREATE_FAILED", "Failed to create comment");
        }

        int rootUpdated = articleCommentMapper.updateRootIdById(entity.getId(), entity.getId());
        if (rootUpdated != 1) {
            throw new BizException("COMMENT_CREATE_FAILED", "Failed to create comment");
        }
        return entity.getId();
    }

    private Long createReplyComment(Long userId, Long articleId, Long parentId, String content) {
        ArticleCommentEntity parent = articleCommentMapper.selectByIdAndArticleId(
                parentId,
                articleId,
                COMMENT_STATUS_NORMAL
        );
        if (parent == null) {
            throw new BizException("PARENT_COMMENT_NOT_FOUND", "Parent comment not found");
        }

        Long rootId = parent.getRootId();
        if (rootId == null || rootId <= 0) {
            rootId = parent.getId();
        }

        ArticleCommentEntity entity = new ArticleCommentEntity();
        entity.setArticleId(articleId);
        entity.setUserId(userId);
        entity.setRootId(rootId);
        entity.setParentId(parent.getId());
        entity.setReplyToUserId(parent.getUserId());
        entity.setContent(content);
        entity.setStatus(COMMENT_STATUS_NORMAL);

        int affected = articleCommentMapper.insert(entity);
        if (affected != 1 || entity.getId() == null) {
            throw new BizException("COMMENT_CREATE_FAILED", "Failed to create comment");
        }
        return entity.getId();
    }

    private void ensurePublishedArticleExists(Long articleId) {
        if (articleMapper.countPublishedById(articleId) != 1) {
            throw new BizException("ARTICLE_NOT_FOUND", "Article not found");
        }
    }

    private void validatePositive(String fieldName, Long value) {
        if (value == null || value <= 0) {
            throw new BizException("INVALID_PARAM", fieldName + " must be a positive number");
        }
    }

    private Pageable requirePageable(int page, int size) {
        if (page < 0 || size <= 0) {
            throw new BizException("INVALID_PARAM", "Invalid pagination parameters: page must be >= 0 and size must be > 0");
        }
        return PageRequest.of(page, size);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private ArticleCommentVO toVO(ArticleCommentEntity entity) {
        return ArticleCommentVO.builder()
                .commentId(entity.getId())
                .articleId(entity.getArticleId())
                .userId(entity.getUserId())
                .rootId(entity.getRootId())
                .parentId(entity.getParentId())
                .replyToUserId(entity.getReplyToUserId())
                .content(entity.getContent())
                .liked(false)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private void applyLikedState(List<ArticleCommentVO> records, Long viewerUserId) {
        if (records.isEmpty() || viewerUserId == null || viewerUserId <= 0) {
            return;
        }

        List<Long> commentIds = records.stream().map(ArticleCommentVO::getCommentId).toList();
        List<Long> likedIds = commentLikeMapper.selectCommentIdsByUserIdAndStatus(
                viewerUserId,
                RELATION_ACTIVE,
                commentIds
        );
        Set<Long> likedIdSet = new HashSet<>(likedIds);
        records.forEach(item -> item.setLiked(likedIdSet.contains(item.getCommentId())));
    }
}
