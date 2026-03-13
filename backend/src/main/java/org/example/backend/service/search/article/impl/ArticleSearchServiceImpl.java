package org.example.backend.service.search.article.impl;

import lombok.RequiredArgsConstructor;
import org.example.backend.common.page.PageUtils;
import org.example.backend.exception.BizException;
import org.example.backend.mapper.search.ArticleSearchMapper;
import org.example.backend.model.dto.ArticleSearchSource;
import org.example.backend.model.es.ArticleSearchDocument;
import org.example.backend.model.vo.ArticleSearchVO;
import org.example.backend.repository.ArticleSearchRepository;
import org.example.backend.service.search.article.ArticleSearchService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticleSearchServiceImpl implements ArticleSearchService {

    private static final int STATUS_PUBLISHED = 1;

    private final ArticleSearchMapper articleSearchMapper;
    private final ArticleSearchRepository articleSearchRepository;

    @Override
    public Page<ArticleSearchVO> search(String keyword, Long userId, int page, int size) {
        if (!StringUtils.hasText(keyword) && userId == null) {
            throw new BizException("INVALID_PARAM", "Missing required query parameter: provide q or userId");
        }
        Pageable pageable = PageUtils.pageable(page, size);
        if (!StringUtils.hasText(keyword)) {
            return articleSearchRepository.findByUserId(userId, pageable).map(this::toSearchVO);
        }
        if (userId == null) {
            return articleSearchRepository.findByTitleContainingOrSummaryContaining(keyword, keyword, pageable)
                    .map(this::toSearchVO);
        }
        return articleSearchRepository
                .findByUserIdAndTitleContainingOrUserIdAndSummaryContaining(userId, keyword, userId, keyword, pageable)
                .map(this::toSearchVO);
    }

    @Override
    public long rebuildIndex() {
        List<ArticleSearchSource> sources = articleSearchMapper.selectAllForSearch();
        List<ArticleSearchDocument> documents = sources.stream()
                .filter(this::isPublished)
                .map(this::toDocument)
                .toList();

        articleSearchRepository.deleteAll();
        articleSearchRepository.saveAll(documents);
        return documents.size();
    }

    @Override
    public void syncByArticleId(Long articleId) {
        if (articleId == null || articleId <= 0) {
            throw new BizException("INVALID_PARAM", "articleId must be a positive number");
        }
        ArticleSearchSource source = articleSearchMapper.selectByArticleIdForSearch(articleId);
        String documentId = String.valueOf(articleId);
        if (!isPublished(source)) {
            articleSearchRepository.deleteById(documentId);
            return;
        }
        articleSearchRepository.save(toDocument(source));
    }

    private boolean isPublished(ArticleSearchSource source) {
        return source != null && source.getStatus() != null && source.getStatus() == STATUS_PUBLISHED;
    }

    private ArticleSearchDocument toDocument(ArticleSearchSource source) {
        return ArticleSearchDocument.builder()
                .id(String.valueOf(source.getId()))
                .articleId(source.getId())
                .userId(source.getUserId())
                .title(source.getTitle())
                .summary(source.getSummary())
                .status(source.getStatus())
                .publishedAt(source.getPublishedAt())
                .updatedAt(source.getUpdatedAt())
                .build();
    }

    private ArticleSearchVO toSearchVO(ArticleSearchDocument document) {
        return ArticleSearchVO.builder()
                .articleId(document.getArticleId())
                .userId(document.getUserId())
                .title(document.getTitle())
                .summary(document.getSummary())
                .build();
    }
}
