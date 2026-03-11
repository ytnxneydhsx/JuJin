package org.example.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.article-like-cache")
public class AppArticleLikeCacheProperties {

    private String likeSetKeyPrefix = "article:likes:";
    private String likeCountKeyPrefix = "article:like_count:";
    private String likeInitKeyPrefix = "article:likes:init:";
    private String dirtyArticleSetKey = "article:likes:dirty";
    private long ttlSeconds = 3600;
    private long flushIntervalMs = 60000;
    private int flushBatchSize = 500;
}
