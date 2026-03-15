package org.example.backend.service.search.user.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.backend.common.page.PageUtils;
import org.example.backend.exception.BizException;
import org.example.backend.model.es.UserSearchDocument;
import org.example.backend.model.vo.UserSearchVO;
import org.example.backend.repository.UserSearchRepository;
import org.example.backend.service.search.user.UserSearchService;
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
public class UserSearchServiceImpl implements UserSearchService {

    private static final String INDEX_NAME = "user_search";

    private final UserSearchRepository userSearchRepository;
    private final ObjectMapper objectMapper;

    @Value("${spring.elasticsearch.uris}")
    private String elasticsearchUris;

    @Override
    public Page<UserSearchVO> search(String keyword, Long userId, int page, int size) {
        if (!StringUtils.hasText(keyword) && userId == null) {
            throw new BizException("INVALID_PARAM", "Missing required query parameter: provide q or userId");
        }
        Pageable pageable = PageUtils.pageable(page, size);
        if (!StringUtils.hasText(keyword)) {
            return userSearchRepository.findByUserId(userId, pageable).map(this::toSearchVO);
        }
        return searchByKeyword(keyword, userId, pageable);
    }

    private Page<UserSearchVO> searchByKeyword(String keyword, Long userId, Pageable pageable) {
        String regexpValue = ".*" + escapeRegexp(keyword.trim()) + ".*";
        int from = Math.toIntExact(pageable.getOffset());
        int size = pageable.getPageSize();
        try {
            JsonNode response = doSearch(buildKeywordPayload(regexpValue, userId, from, size));
            List<UserSearchVO> records = new ArrayList<>();
            for (JsonNode hit : response.path("hits").path("hits")) {
                UserSearchDocument document = objectMapper.treeToValue(hit.path("_source"), UserSearchDocument.class);
                records.add(toSearchVO(document));
            }
            long total = response.path("hits").path("total").path("value").asLong(records.size());
            return new PageImpl<>(records, pageable, total);
        } catch (IOException e) {
            throw new IllegalStateException("search user index failed", e);
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
            throw new IllegalStateException("search user index failed: empty response");
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
                .put("name", regexpValue);
        keywordBool.withArray("should")
                .addObject()
                .putObject("regexp")
                .put("account", regexpValue);

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

    private String resolveBaseUri() {
        return elasticsearchUris.split(",")[0].trim().replaceAll("/+$", "");
    }

    private UserSearchVO toSearchVO(UserSearchDocument document) {
        return UserSearchVO.builder()
                .id(document.getUserId())
                .name(document.getName())
                .build();
    }
}
