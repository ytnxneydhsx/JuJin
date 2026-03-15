package org.example.backend.service.search.article.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.backend.common.constant.AppConstants;
import org.example.backend.common.page.PageUtils;
import org.example.backend.exception.BizException;
import org.example.backend.mapper.search.ArticleSearchMapper;
import org.example.backend.model.dto.ArticleSearchSource;
import org.example.backend.model.es.ArticleSearchDocument;
import org.example.backend.model.vo.ArticleSearchVO;
import org.example.backend.repository.ArticleSearchRepository;
import org.example.backend.service.search.article.ArticleSearchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticleSearchServiceImpl implements ArticleSearchService {

    private static final String INDEX_NAME = "article_search";

    private final ArticleSearchMapper articleSearchMapper;
    private final ArticleSearchRepository articleSearchRepository;
    private final ObjectMapper objectMapper;

    @Value("${spring.elasticsearch.uris}")
    private String elasticsearchUris;

    @Override
    public Page<ArticleSearchVO> search(String keyword, Long userId, int page, int size) {
        if (!StringUtils.hasText(keyword) && userId == null) {
            throw new BizException("INVALID_PARAM", "Missing required query parameter: provide q or userId");
        }
        Pageable pageable = PageUtils.pageable(page, size);
        if (!StringUtils.hasText(keyword)) {
            return articleSearchRepository.findByUserId(userId, pageable).map(this::toSearchVO);
        }
        if (containsHanCharacter(keyword)) {
            return searchPublishedByKeywordFromMysql(keyword, userId, pageable);
        }
        return searchByKeyword(keyword, userId, pageable);
    }

    private Page<ArticleSearchVO> searchPublishedByKeywordFromMysql(String keyword, Long userId, Pageable pageable) {
        List<ArticleSearchVO> records = articleSearchMapper.searchPublishedByKeyword(
                        keyword.trim(),
                        userId,
                        AppConstants.ArticleStatus.PUBLISHED,
                        Math.toIntExact(pageable.getOffset()),
                        pageable.getPageSize()
                )
                .stream()
                .map(this::toSearchVO)
                .toList();
        long total = articleSearchMapper.countPublishedByKeyword(
                keyword.trim(),
                userId,
                AppConstants.ArticleStatus.PUBLISHED
        );
        return new PageImpl<>(records, pageable, total);
    }

    private Page<ArticleSearchVO> searchByKeyword(String keyword, Long userId, Pageable pageable) {
        String regexpValue = ".*" + escapeRegexp(keyword.trim()) + ".*";
        int from = Math.toIntExact(pageable.getOffset());
        int size = pageable.getPageSize();
        try {
            JsonNode payload = buildKeywordPayload(regexpValue, userId, from, size);
            JsonNode response = doSearch(payload);
            List<ArticleSearchVO> records = new ArrayList<>();
            for (JsonNode hit : response.path("hits").path("hits")) {
                ArticleSearchDocument document = objectMapper.treeToValue(hit.path("_source"), ArticleSearchDocument.class);
                records.add(toSearchVO(document));
            }
            long total = response.path("hits").path("total").path("value").asLong(records.size());
            return new PageImpl<>(records, pageable, total);
        } catch (IOException e) {
            throw new IllegalStateException("search article index failed", e);
        }
    }

    private JsonNode doSearch(JsonNode payload) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<byte[]> entity = new HttpEntity<>(objectMapper.writeValueAsBytes(payload), headers);
        ResponseEntity<byte[]> response = new RestTemplate().exchange(
                resolveBaseUri() + "/" + INDEX_NAME + "/_search",
                HttpMethod.POST,
                entity,
                byte[].class
        );
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("search article index failed: empty response");
        }
        return objectMapper.readTree(response.getBody());
    }

    private JsonNode buildKeywordPayload(String regexpValue, Long userId, int from, int size) {
        var root = objectMapper.createObjectNode();
        root.put("from", from);
        root.put("size", size);
        root.put("track_total_hits", true);

        var must = root.putObject("query")
                .putObject("bool")
                .putArray("must");

        var keywordBool = must.addObject()
                .putObject("bool");
        keywordBool.put("minimum_should_match", 1);
        keywordBool.putArray("should")
                .addObject()
                .putObject("regexp")
                .put("title", regexpValue);
        keywordBool.withArray("should")
                .addObject()
                .putObject("regexp")
                .put("summary", regexpValue);

        if (userId != null) {
            must.addObject()
                    .putObject("term")
                    .put("userId", userId);
        }
        return root;
    }

    private String escapeRegexp(String value) {
        StringBuilder escaped = new StringBuilder(value.length() * 2);
        for (char ch : value.toCharArray()) {
            if ("\\.?+*|{}[]()\"#@&<>~".indexOf(ch) >= 0) {
                escaped.append('\\');
            }
            escaped.append(ch);
        }
        return escaped.toString();
    }

    private boolean containsHanCharacter(String value) {
        return value.codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private String resolveBaseUri() {
        return elasticsearchUris.split(",")[0].trim().replaceAll("/+$", "");
    }

    private ArticleSearchVO toSearchVO(ArticleSearchSource source) {
        return ArticleSearchVO.builder()
                .articleId(source.getId())
                .userId(source.getUserId())
                .title(source.getTitle())
                .summary(source.getSummary())
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
