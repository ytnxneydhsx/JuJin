package org.example.backend.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleCommentVO {

    private Long commentId;
    private Long articleId;
    private Long userId;
    private Long rootId;
    private Long parentId;
    private Long replyToUserId;
    private String content;
    private Boolean liked;
    private Long childCount;
    @Builder.Default
    private List<ArticleCommentVO> children = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
