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

    @Select({
            "<script>",
            "SELECT",
            "  id AS id,",
            "  user_id AS userId,",
            "  title AS title,",
            "  summary AS summary,",
            "  status AS status,",
            "  published_at AS publishedAt,",
            "  updated_at AS updatedAt",
            "FROM article",
            "WHERE status = #{publishedStatus}",
            "  AND (title LIKE CONCAT('%', #{keyword}, '%') OR summary LIKE CONCAT('%', #{keyword}, '%'))",
            "  <if test='userId != null'>",
            "    AND user_id = #{userId}",
            "  </if>",
            "ORDER BY published_at DESC, id DESC",
            "LIMIT #{size} OFFSET #{offset}",
            "</script>"
    })
    List<ArticleSearchSource> searchPublishedByKeyword(@Param("keyword") String keyword,
                                                       @Param("userId") Long userId,
                                                       @Param("publishedStatus") Integer publishedStatus,
                                                       @Param("offset") int offset,
                                                       @Param("size") int size);

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM article",
            "WHERE status = #{publishedStatus}",
            "  AND (title LIKE CONCAT('%', #{keyword}, '%') OR summary LIKE CONCAT('%', #{keyword}, '%'))",
            "  <if test='userId != null'>",
            "    AND user_id = #{userId}",
            "  </if>",
            "</script>"
    })
    long countPublishedByKeyword(@Param("keyword") String keyword,
                                 @Param("userId") Long userId,
                                 @Param("publishedStatus") Integer publishedStatus);
}
