package org.example.backend.service.search.article.impl;

import lombok.RequiredArgsConstructor;
import org.example.backend.common.constant.AppConstants.ArticleStatus;
import org.example.backend.exception.BizException;
import org.example.backend.mapper.search.ArticleSearchMapper;
import org.example.backend.model.dto.ArticleSearchSource;
import org.example.backend.model.es.ArticleSearchDocument;
import org.example.backend.repository.ArticleSearchRepository;
import org.example.backend.service.search.article.ArticleSearchIndexService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticleSearchIndexServiceImpl implements ArticleSearchIndexService {

    private final ArticleSearchMapper articleSearchMapper;
    private final ArticleSearchRepository articleSearchRepository;

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
        return source != null && source.getStatus() != null && source.getStatus() == ArticleStatus.PUBLISHED;
    }

    private ArticleSearchDocument toDocument(ArticleSearchSource source) {
        return ArticleSearchDocument.builder()
                .id(String.valueOf(source.getId()))
                .articleId(source.getId())
                .userId(source.getUserId())
                .title(source.getTitle())
                .summary(source.getSummary())
                .status(source.getStatus())
                .build();
    }
}
