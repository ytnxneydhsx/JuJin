package org.example.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.security.cors")
public class AppSecurityProperties {

    /**
     * Comma-separated origins, e.g.:
     * http://localhost:5173,http://127.0.0.1:5173
     */
    private String allowedOrigins = "http://localhost:5173,http://127.0.0.1:5173";
}

