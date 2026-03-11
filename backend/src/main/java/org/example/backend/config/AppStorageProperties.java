package org.example.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.storage")
public class AppStorageProperties {

    private String endpoint = "http://localhost:9000";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin";
    private String bucket = "juejin-media";
    private String publicBaseUrl = "http://localhost/uploads";
    private long maxImageSizeBytes = 10 * 1024 * 1024;
    private List<String> allowedContentTypes = List.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );
    private List<String> allowedExtensions = List.of(
            "jpg",
            "jpeg",
            "png",
            "webp",
            "gif"
    );
}
