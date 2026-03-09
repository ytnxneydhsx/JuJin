package org.example.backend.repository;

import org.example.backend.model.es.ArticleSearchDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ArticleSearchRepository extends ElasticsearchRepository<ArticleSearchDocument, String> {

    Page<ArticleSearchDocument> findByUserId(Long userId, Pageable pageable);

    Page<ArticleSearchDocument> findByTitleContainingOrSummaryContaining(String titleKeyword,
                                                                         String summaryKeyword,
                                                                         Pageable pageable);

    Page<ArticleSearchDocument> findByUserIdAndTitleContainingOrUserIdAndSummaryContaining(Long userIdForTitle,
                                                                                             String titleKeyword,
                                                                                             Long userIdForSummary,
                                                                                             String summaryKeyword,
                                                                                             Pageable pageable);
}
