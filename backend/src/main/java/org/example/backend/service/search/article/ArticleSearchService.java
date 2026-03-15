package org.example.backend.service.search.article;

import org.example.backend.model.vo.ArticleSearchVO;
import org.springframework.data.domain.Page;

public interface ArticleSearchService {

    Page<ArticleSearchVO> search(String keyword, Long userId, int page, int size);
}
