package org.example.backend.model.dto.comment;

import lombok.Data;

@Data
public class CommentChildCountDTO {

    private Long parentId;
    private Long childCount;
}
