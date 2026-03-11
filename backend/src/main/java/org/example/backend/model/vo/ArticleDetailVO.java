package org.example.backend.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleDetailVO {

    private Long articleId;
    private Long userId;
    private String title;
    private String summary;
    private String coverUrl;
    private String content;
    private Integer status;
    private Long likeCount;
    private Long favoriteCount;
    private Long viewCount;
    private Boolean liked;
    private Boolean favorited;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
