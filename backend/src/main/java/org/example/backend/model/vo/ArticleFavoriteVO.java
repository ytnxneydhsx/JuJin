package org.example.backend.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleFavoriteVO {

    private Long articleId;
    private Boolean favorited;
    private Long favoriteCount;
}
