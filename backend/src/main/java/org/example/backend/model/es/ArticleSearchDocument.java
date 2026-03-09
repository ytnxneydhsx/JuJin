package org.example.backend.model.es;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "article_search")
public class ArticleSearchDocument {

    @Id
    private String id;

    @Field(type = FieldType.Long)
    private Long articleId;

    @Field(type = FieldType.Long)
    private Long userId;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Text)
    private String summary;

    @Field(type = FieldType.Integer, index = false)
    private Integer status;

    @Field(type = FieldType.Date, index = false)
    private LocalDateTime publishedAt;

    @Field(type = FieldType.Date, index = false)
    private LocalDateTime updatedAt;
}
