package org.example.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.article-feed-cache")
public class AppArticleFeedCacheProperties {

    private String latestZsetKey = "article:feed:latest";
    private String rankingZsetKey = "article:feed:ranking";
    private String cardHashKeyPrefix = "article:feed:card:";
    private String likeUserSetKeyPrefix = "article:feed:like:users:";
    private String favoriteUserSetKeyPrefix = "article:feed:favorite:users:";
    private String likeInitKeyPrefix = "article:feed:like:init:";
    private String favoriteInitKeyPrefix = "article:feed:favorite:init:";
    private String dirtyArticleSetKey = "article:feed:dirty";
    private int maxItems = 500;
    private long rebuildIntervalMs = 300000;
}
