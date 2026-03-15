package org.example.backend.model.es;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Document(indexName = "user_search")
public class UserSearchDocument {

    @Id
    private String id;

    @Field(type = FieldType.Long)
    private Long userId;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Text)
    private String account;

    @Field(type = FieldType.Text, index = false)
    private String sign;

    @Field(type = FieldType.Keyword, index = false)
    private String avatarUrl;

    @Field(type = FieldType.Integer)
    private Integer status;
}
