package org.example.backend.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArticleCommentEntity {

    private Long id;
    private Long articleId;
    private Long userId;
    private Long rootId;
    private Long parentId;
    private Long replyToUserId;
    private String content;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
