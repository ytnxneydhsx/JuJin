package org.example.backend.service.core.article.support;

import lombok.RequiredArgsConstructor;
import org.example.backend.common.page.PageUtils;
import org.example.backend.model.vo.ArticleSummaryVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ArticleFeedQuerySupport {

    private final StringRedisTemplate stringRedisTemplate;
    private final ArticleFeedCacheKeys articleFeedCacheKeys;
    private final ArticleFeedCardSupport articleFeedCardSupport;
    private final ArticleFeedStatsSupport articleFeedStatsSupport;

    public Page<ArticleSummaryVO> listFeed(String sortBy, int page, int size) {
        Pageable pageable = PageUtils.pageable(page, size);
        String zsetKey = articleFeedCacheKeys.feedKey(sortBy);
        Long total = stringRedisTemplate.opsForZSet().zCard(zsetKey);
        if (total == null || total <= 0L) {
            return PageUtils.page(Collections.emptyList(), pageable, 0L);
        }

        int start = PageUtils.offset(pageable);
        int end = start + pageable.getPageSize() - 1;
        Set<String> articleIdTexts = stringRedisTemplate.opsForZSet().reverseRange(zsetKey, start, end);
        if (articleIdTexts == null || articleIdTexts.isEmpty()) {
            return PageUtils.page(Collections.emptyList(), pageable, total);
        }

        List<ArticleSummaryVO> records = new ArrayList<>(articleIdTexts.size());
        for (String articleIdText : articleIdTexts) {
            Long articleId = parseLong(articleIdText);
            if (articleId == null) {
                continue;
            }
            ArticleSummaryVO summary = articleFeedCardSupport.getOrLoadSummary(articleId);
            if (summary != null) {
                records.add(summary);
            }
        }
        articleFeedStatsSupport.applyStats(records);
        return PageUtils.page(records, pageable, total);
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
