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
public class ArticleDraftVO {

    private Long draftId;
    private Long articleId;
    private String title;
    private String summary;
    private String coverUrl;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
