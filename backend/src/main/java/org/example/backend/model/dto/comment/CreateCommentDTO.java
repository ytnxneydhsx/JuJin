package org.example.backend.model.dto.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCommentDTO {

    @Positive(message = "articleId must be positive")
    private Long articleId;

    @Positive(message = "parentId must be positive")
    private Long parentId;

    @NotBlank(message = "content cannot be blank")
    @Size(max = 3000, message = "content length must be <= 3000")
    private String content;
}
