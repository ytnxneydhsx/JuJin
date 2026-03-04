package org.example.backend.service.article.impl;

import lombok.RequiredArgsConstructor;
import org.example.backend.exception.BizException;
import org.example.backend.mapper.base.ArticleDraftMapper;
import org.example.backend.mapper.base.ArticleMapper;
import org.example.backend.model.dto.article.SaveDraftDTO;
import org.example.backend.model.dto.article.UpdateArticleDTO;
import org.example.backend.model.entity.ArticleDraftEntity;
import org.example.backend.model.entity.ArticleEntity;
import org.example.backend.model.vo.ArticleDetailVO;
import org.example.backend.model.vo.ArticleDraftVO;
import org.example.backend.model.vo.ArticleSummaryVO;
import org.example.backend.service.article.ArticleService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    private static final int STATUS_PUBLISHED = 1;
    private static final int STATUS_HIDDEN = 2;

    private final ArticleMapper articleMapper;
    private final ArticleDraftMapper articleDraftMapper;

    @Override
    @Transactional
    public Long createDraft(Long userId, SaveDraftDTO dto) {
        validateUserId(userId);
        ArticleDraftEntity entity = new ArticleDraftEntity();
        entity.setUserId(userId);
        entity.setArticleId(dto.getArticleId());
        entity.setTitle(trimToNull(dto.getTitle()));
        entity.setSummary(trimToNull(dto.getSummary()));
        entity.setCoverUrl(trimToNull(dto.getCoverUrl()));
        entity.setContent(dto.getContent());

        int affected = articleDraftMapper.insert(entity);
        if (affected != 1 || entity.getId() == null) {
            throw new BizException("DRAFT_CREATE_FAILED", "Failed to create draft");
        }
        return entity.getId();
    }

    @Override
    @Transactional
    public void updateDraft(Long userId, Long draftId, SaveDraftDTO dto) {
        validateUserId(userId);
        validatePositive("draftId", draftId);
        int affected = articleDraftMapper.updateByIdAndUserId(
                draftId,
                userId,
                dto.getArticleId(),
                trimToNull(dto.getTitle()),
                trimToNull(dto.getSummary()),
                trimToNull(dto.getCoverUrl()),
                dto.getContent()
        );
        if (affected != 1) {
            throw new BizException("DRAFT_NOT_FOUND", "Draft not found");
        }
    }

    @Override
    public ArticleDraftVO getDraft(Long userId, Long draftId) {
        validateUserId(userId);
        validatePositive("draftId", draftId);
        ArticleDraftEntity draft = articleDraftMapper.selectByIdAndUserId(draftId, userId);
        if (draft == null) {
            throw new BizException("DRAFT_NOT_FOUND", "Draft not found");
        }
        return toDraftVO(draft);
    }

    @Override
    public Page<ArticleDraftVO> listDrafts(Long userId, int page, int size) {
        validateUserId(userId);
        Pageable pageable = requirePageable(page, size);
        int offset = Math.toIntExact(pageable.getOffset());

        List<ArticleDraftVO> records = articleDraftMapper.selectPageByUserId(userId, offset, pageable.getPageSize())
                .stream()
                .map(this::toDraftVO)
                .toList();
        long total = articleDraftMapper.countByUserId(userId);
        return new PageImpl<>(records, pageable, total);
    }

    @Override
    @Transactional
    public Long publishDraft(Long userId, Long draftId) {
        validateUserId(userId);
        validatePositive("draftId", draftId);

        ArticleDraftEntity draft = articleDraftMapper.selectByIdAndUserId(draftId, userId);
        if (draft == null) {
            throw new BizException("DRAFT_NOT_FOUND", "Draft not found");
        }

        String title = trimToNull(draft.getTitle());
        String content = trimToNull(draft.getContent());
        if (!StringUtils.hasText(title) || !StringUtils.hasText(content)) {
            throw new BizException("INVALID_PARAM", "Draft title and content are required before publishing");
        }

        Long articleId = draft.getArticleId();
        if (articleId == null) {
            ArticleEntity entity = new ArticleEntity();
            entity.setUserId(userId);
            entity.setTitle(title);
            entity.setSummary(trimToNull(draft.getSummary()));
            entity.setCoverUrl(trimToNull(draft.getCoverUrl()));
            entity.setContent(content);
            entity.setStatus(STATUS_PUBLISHED);
            int inserted = articleMapper.insert(entity);
            if (inserted != 1 || entity.getId() == null) {
                throw new BizException("ARTICLE_PUBLISH_FAILED", "Failed to publish article");
            }
            articleId = entity.getId();
        } else {
            ArticleEntity existingArticle = articleMapper.selectByIdAndUserId(articleId, userId);
            if (existingArticle == null) {
                throw new BizException("ARTICLE_NOT_FOUND", "Related article not found");
            }
            int updated = articleMapper.updateByIdAndUserId(
                    articleId,
                    userId,
                    title,
                    trimToNull(draft.getSummary()),
                    trimToNull(draft.getCoverUrl()),
                    content,
                    STATUS_PUBLISHED
            );
            if (updated != 1) {
                throw new BizException("ARTICLE_PUBLISH_FAILED", "Failed to publish article");
            }
        }

        articleDraftMapper.updateArticleIdByIdAndUserId(draftId, userId, articleId);
        return articleId;
    }

    @Override
    @Transactional
    public void updateArticle(Long userId, Long articleId, UpdateArticleDTO dto) {
        validateUserId(userId);
        validatePositive("articleId", articleId);
        ArticleEntity existingArticle = articleMapper.selectByIdAndUserId(articleId, userId);
        if (existingArticle == null) {
            throw new BizException("ARTICLE_NOT_FOUND", "Article not found");
        }

        String title = trimToNull(dto.getTitle());
        String content = trimToNull(dto.getContent());
        if (!StringUtils.hasText(title) || !StringUtils.hasText(content)) {
            throw new BizException("INVALID_PARAM", "title and content cannot be blank");
        }

        Integer status = dto.getStatus() == null ? existingArticle.getStatus() : dto.getStatus();
        if (status == null || (status != STATUS_PUBLISHED && status != STATUS_HIDDEN)) {
            throw new BizException("INVALID_PARAM", "status must be 1 or 2");
        }

        int affected = articleMapper.updateByIdAndUserId(
                articleId,
                userId,
                title,
                trimToNull(dto.getSummary()),
                trimToNull(dto.getCoverUrl()),
                content,
                status
        );
        if (affected != 1) {
            throw new BizException("ARTICLE_UPDATE_FAILED", "Failed to update article");
        }
    }

    @Override
    @Transactional
    public void deleteArticle(Long userId, Long articleId) {
        validateUserId(userId);
        validatePositive("articleId", articleId);
        int affected = articleMapper.softDeleteByIdAndUserId(articleId, userId);
        if (affected != 1) {
            throw new BizException("ARTICLE_NOT_FOUND", "Article not found");
        }
    }

    @Override
    public ArticleDetailVO getMyArticle(Long userId, Long articleId) {
        validateUserId(userId);
        validatePositive("articleId", articleId);
        ArticleEntity article = articleMapper.selectByIdAndUserId(articleId, userId);
        if (article == null) {
            throw new BizException("ARTICLE_NOT_FOUND", "Article not found");
        }
        return toDetailVO(article);
    }

    @Override
    public Page<ArticleSummaryVO> listMyArticles(Long userId, int page, int size) {
        validateUserId(userId);
        Pageable pageable = requirePageable(page, size);
        int offset = Math.toIntExact(pageable.getOffset());

        List<ArticleSummaryVO> records = articleMapper.selectPageByUserId(userId, offset, pageable.getPageSize())
                .stream()
                .map(this::toSummaryVO)
                .toList();
        long total = articleMapper.countByUserId(userId);
        return new PageImpl<>(records, pageable, total);
    }

    @Override
    public ArticleDetailVO getPublishedArticle(Long articleId) {
        validatePositive("articleId", articleId);
        ArticleEntity article = articleMapper.selectPublishedById(articleId);
        if (article == null) {
            throw new BizException("ARTICLE_NOT_FOUND", "Article not found");
        }
        return toDetailVO(article);
    }

    @Override
    public Page<ArticleSummaryVO> listPublishedArticles(Long authorUserId, int page, int size) {
        if (authorUserId != null && authorUserId <= 0) {
            throw new BizException("INVALID_PARAM", "userId must be a positive number");
        }
        Pageable pageable = requirePageable(page, size);
        int offset = Math.toIntExact(pageable.getOffset());

        List<ArticleSummaryVO> records = articleMapper.selectPublishedPage(authorUserId, offset, pageable.getPageSize())
                .stream()
                .map(this::toSummaryVO)
                .toList();
        long total = articleMapper.countPublished(authorUserId);
        return new PageImpl<>(records, pageable, total);
    }

    private void validateUserId(Long userId) {
        validatePositive("userId", userId);
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

    private ArticleDraftVO toDraftVO(ArticleDraftEntity entity) {
        return ArticleDraftVO.builder()
                .draftId(entity.getId())
                .articleId(entity.getArticleId())
                .title(entity.getTitle())
                .summary(entity.getSummary())
                .coverUrl(entity.getCoverUrl())
                .content(entity.getContent())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private ArticleSummaryVO toSummaryVO(ArticleEntity entity) {
        return ArticleSummaryVO.builder()
                .articleId(entity.getId())
                .userId(entity.getUserId())
                .title(entity.getTitle())
                .summary(entity.getSummary())
                .coverUrl(entity.getCoverUrl())
                .publishedAt(entity.getPublishedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private ArticleDetailVO toDetailVO(ArticleEntity entity) {
        return ArticleDetailVO.builder()
                .articleId(entity.getId())
                .userId(entity.getUserId())
                .title(entity.getTitle())
                .summary(entity.getSummary())
                .coverUrl(entity.getCoverUrl())
                .content(entity.getContent())
                .status(entity.getStatus())
                .publishedAt(entity.getPublishedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
