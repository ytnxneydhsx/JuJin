package org.example.backend.model.dto.article;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SaveDraftDTO {

    @Positive(message = "articleId must be positive")
    private Long articleId;

    @Size(max = 200, message = "title length must be <= 200")
    private String title;

    @Size(max = 500, message = "summary length must be <= 500")
    private String summary;

    @Size(max = 512, message = "coverUrl length must be <= 512")
    private String coverUrl;

    private String content;
}
