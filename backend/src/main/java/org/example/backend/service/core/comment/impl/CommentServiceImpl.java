package org.example.backend.service.core.comment.impl;

import lombok.RequiredArgsConstructor;
import org.example.backend.common.constant.AppConstants.CommentStatus;
import org.example.backend.common.constant.AppConstants.RelationStatus;
import org.example.backend.common.page.PageUtils;
import org.example.backend.exception.BizException;
import org.example.backend.mapper.article.ArticleMapper;
import org.example.backend.mapper.comment.ArticleCommentMapper;
import org.example.backend.mapper.interaction.CommentLikeMapper;
import org.example.backend.model.dto.comment.CommentChildCountDTO;
import org.example.backend.model.dto.comment.CreateCommentDTO;
import org.example.backend.model.entity.ArticleCommentEntity;
import org.example.backend.model.vo.ArticleCommentVO;
import org.example.backend.service.core.comment.CommentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private static final int ROOT_CHILD_PREVIEW_SIZE = 5;

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
                CommentStatus.DELETED,
                CommentStatus.NORMAL
        );
        if (affected != 1) {
            throw new BizException("COMMENT_NOT_FOUND", "Comment not found");
        }
    }

    @Override
    public Page<ArticleCommentVO> listRootComments(Long viewerUserId, Long articleId, int page, int size) {
        validatePositive("articleId", articleId);
        Pageable pageable = PageUtils.pageable(page, size);
        int offset = PageUtils.offset(pageable);

        List<ArticleCommentEntity> rootEntities = articleCommentMapper.selectRootPageByArticleId(
                articleId,
                CommentStatus.NORMAL,
                offset,
                pageable.getPageSize()
        );
        List<ArticleCommentVO> records = buildRootCommentTree(articleId, rootEntities, viewerUserId);
        long total = articleCommentMapper.countRootByArticleId(articleId, CommentStatus.NORMAL);
        return PageUtils.page(records, pageable, total);
    }

    @Override
    public Page<ArticleCommentVO> listChildComments(Long viewerUserId, Long articleId, Long parentId, int page, int size) {
        validatePositive("articleId", articleId);
        validatePositive("parentId", parentId);
        ensureCommentExists(articleId, parentId);
        Pageable pageable = PageUtils.pageable(page, size);
        int offset = PageUtils.offset(pageable);

        List<ArticleCommentEntity> childEntities = articleCommentMapper.selectChildPageByArticleIdAndParentId(
                articleId,
                parentId,
                CommentStatus.NORMAL,
                offset,
                pageable.getPageSize()
        );
        List<ArticleCommentVO> records = buildCommentVOs(articleId, childEntities, viewerUserId);
        long total = articleCommentMapper.countChildByArticleIdAndParentId(articleId, parentId, CommentStatus.NORMAL);
        return PageUtils.page(records, pageable, total);
    }

    private Long createRootComment(Long userId, Long articleId, String content) {
        ArticleCommentEntity entity = new ArticleCommentEntity();
        entity.setArticleId(articleId);
        entity.setUserId(userId);
        entity.setRootId(0L);
        entity.setParentId(null);
        entity.setReplyToUserId(null);
        entity.setContent(content);
        entity.setStatus(CommentStatus.NORMAL);

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
                CommentStatus.NORMAL
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
        entity.setStatus(CommentStatus.NORMAL);

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

    private void ensureCommentExists(Long articleId, Long commentId) {
        ArticleCommentEntity entity = articleCommentMapper.selectByIdAndArticleId(
                commentId,
                articleId,
                CommentStatus.NORMAL
        );
        if (entity == null) {
            throw new BizException("COMMENT_NOT_FOUND", "Comment not found");
        }
    }

    private void validatePositive(String fieldName, Long value) {
        if (value == null || value <= 0) {
            throw new BizException("INVALID_PARAM", fieldName + " must be a positive number");
        }
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
                .childCount(0L)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private List<ArticleCommentVO> buildRootCommentTree(Long articleId,
                                                        List<ArticleCommentEntity> rootEntities,
                                                        Long viewerUserId) {
        if (rootEntities.isEmpty()) {
            return Collections.emptyList();
        }

        List<ArticleCommentVO> roots = buildCommentVOs(articleId, rootEntities, viewerUserId);
        List<Long> rootIds = roots.stream().map(ArticleCommentVO::getCommentId).toList();
        List<ArticleCommentEntity> previewEntities = articleCommentMapper.selectChildPreviewByArticleIdAndParentIds(
                articleId,
                rootIds,
                CommentStatus.NORMAL,
                ROOT_CHILD_PREVIEW_SIZE
        );
        List<ArticleCommentVO> previewChildren = buildCommentVOs(articleId, previewEntities, viewerUserId);
        Map<Long, List<ArticleCommentVO>> previewByParentId = previewChildren.stream()
                .collect(Collectors.groupingBy(ArticleCommentVO::getParentId));

        roots.forEach(root -> root.setChildren(new ArrayList<>(previewByParentId.getOrDefault(
                root.getCommentId(),
                Collections.emptyList()
        ))));
        return roots;
    }

    private List<ArticleCommentVO> buildCommentVOs(Long articleId,
                                                   List<ArticleCommentEntity> entities,
                                                   Long viewerUserId) {
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }

        List<ArticleCommentVO> records = entities.stream()
                .map(this::toVO)
                .collect(Collectors.toCollection(ArrayList::new));
        Map<Long, Long> childCountMap = loadChildCountMap(
                articleId,
                records.stream().map(ArticleCommentVO::getCommentId).toList()
        );
        records.forEach(item -> item.setChildCount(childCountMap.getOrDefault(item.getCommentId(), 0L)));
        applyLikedState(records, viewerUserId);
        return records;
    }

    private Map<Long, Long> loadChildCountMap(Long articleId, List<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return articleCommentMapper.selectChildCountsByArticleIdAndParentIds(
                        articleId,
                        parentIds,
                        CommentStatus.NORMAL
                )
                .stream()
                .collect(Collectors.toMap(CommentChildCountDTO::getParentId, CommentChildCountDTO::getChildCount));
    }

    private void applyLikedState(List<ArticleCommentVO> records, Long viewerUserId) {
        if (records.isEmpty() || viewerUserId == null || viewerUserId <= 0) {
            return;
        }

        List<Long> commentIds = records.stream().map(ArticleCommentVO::getCommentId).toList();
        List<Long> likedIds = commentLikeMapper.selectCommentIdsByUserIdAndStatus(
                viewerUserId,
                RelationStatus.ACTIVE,
                commentIds
        );
        Set<Long> likedIdSet = new HashSet<>(likedIds);
        records.forEach(item -> item.setLiked(likedIdSet.contains(item.getCommentId())));
    }
}
