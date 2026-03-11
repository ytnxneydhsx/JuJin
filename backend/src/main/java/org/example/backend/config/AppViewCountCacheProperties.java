package org.example.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.view-count-cache")
public class AppViewCountCacheProperties {

    private String keyPrefix = "article:view:count:";
    private long ttlSeconds = 3600;
    private long flushIntervalMs = 60000;
}
