package org.example.backend.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArticleDraftEntity {

    private Long id;
    private Long userId;
    private Long articleId;
    private String title;
    private String summary;
    private String coverUrl;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
