package org.example.backend.model.dto.article;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateArticleDTO {

    @NotBlank(message = "title cannot be blank")
    @Size(max = 200, message = "title length must be <= 200")
    private String title;

    @Size(max = 500, message = "summary length must be <= 500")
    private String summary;

    @Size(max = 512, message = "coverUrl length must be <= 512")
    private String coverUrl;

    @NotBlank(message = "content cannot be blank")
    private String content;

    @Min(value = 1, message = "status must be 1 or 2")
    @Max(value = 2, message = "status must be 1 or 2")
    private Integer status;
}
