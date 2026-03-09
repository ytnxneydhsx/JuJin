package org.example.backend.mapper.search;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.backend.model.dto.ArticleSearchSource;

import java.util.List;

@Mapper
public interface ArticleSearchMapper {

    @Select("""
            SELECT
              id AS id,
              user_id AS userId,
              title AS title,
              summary AS summary,
              status AS status,
              published_at AS publishedAt,
              updated_at AS updatedAt
            FROM article
            ORDER BY id ASC
            """)
    List<ArticleSearchSource> selectAllForSearch();

    @Select("""
            SELECT
              id AS id,
              user_id AS userId,
              title AS title,
              summary AS summary,
              status AS status,
              published_at AS publishedAt,
              updated_at AS updatedAt
            FROM article
            WHERE id = #{articleId}
            """)
    ArticleSearchSource selectByArticleIdForSearch(@Param("articleId") Long articleId);
}
