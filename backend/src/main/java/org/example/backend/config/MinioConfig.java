package org.example.backend.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(AppStorageProperties appStorageProperties) {
        return MinioClient.builder()
                .endpoint(appStorageProperties.getEndpoint())
                .credentials(appStorageProperties.getAccessKey(), appStorageProperties.getSecretKey())
                .build();
    }
}
