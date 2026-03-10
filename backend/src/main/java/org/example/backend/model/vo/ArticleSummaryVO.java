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
public class ArticleSummaryVO {

    private Long articleId;
    private Long userId;
    private String title;
    private String summary;
    private String coverUrl;
    private Long likeCount;
    private Long favoriteCount;
    private Boolean liked;
    private Boolean favorited;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;
}
