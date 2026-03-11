package org.example.backend.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArticleEntity {

    private Long id;
    private Long userId;
    private String title;
    private String summary;
    private String coverUrl;
    private String content;
    private Integer status;
    private Long likeCount;
    private Long favoriteCount;
    private Long viewCount;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
