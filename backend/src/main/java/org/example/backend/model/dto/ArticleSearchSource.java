package org.example.backend.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArticleSearchSource {
    private Long id;
    private Long userId;
    private String title;
    private String summary;
    private Integer status;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;
}
