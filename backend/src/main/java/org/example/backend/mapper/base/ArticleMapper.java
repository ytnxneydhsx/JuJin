package org.example.backend.mapper.base;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.backend.model.entity.ArticleEntity;

import java.util.List;

@Mapper
public interface ArticleMapper {

    @Insert("""
            INSERT INTO article(user_id, title, summary, cover_url, content, status, published_at)
            VALUES(#{userId}, #{title}, #{summary}, #{coverUrl}, #{content}, #{status}, NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ArticleEntity entity);

    @Update("""
            UPDATE article
            SET title = #{title},
                summary = #{summary},
                cover_url = #{coverUrl},
                content = #{content},
                status = #{status}
            WHERE id = #{articleId}
              AND user_id = #{userId}
              AND status <> 3
            """)
    int updateByIdAndUserId(@Param("articleId") Long articleId,
                            @Param("userId") Long userId,
                            @Param("title") String title,
                            @Param("summary") String summary,
                            @Param("coverUrl") String coverUrl,
                            @Param("content") String content,
                            @Param("status") Integer status);

    @Update("""
            UPDATE article
            SET status = 3
            WHERE id = #{articleId}
              AND user_id = #{userId}
              AND status <> 3
            """)
    int softDeleteByIdAndUserId(@Param("articleId") Long articleId,
                                @Param("userId") Long userId);

    @Select("""
            SELECT
              id AS id,
              user_id AS userId,
              title AS title,
              summary AS summary,
              cover_url AS coverUrl,
              content AS content,
              status AS status,
              published_at AS publishedAt,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM article
            WHERE id = #{articleId}
              AND user_id = #{userId}
              AND status <> 3
            """)
    ArticleEntity selectByIdAndUserId(@Param("articleId") Long articleId,
                                      @Param("userId") Long userId);

    @Select("""
            SELECT
              id AS id,
              user_id AS userId,
              title AS title,
              summary AS summary,
              cover_url AS coverUrl,
              content AS content,
              status AS status,
              published_at AS publishedAt,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM article
            WHERE id = #{articleId}
              AND status = 1
            """)
    ArticleEntity selectPublishedById(@Param("articleId") Long articleId);

    @Select("""
            SELECT
              id AS id,
              user_id AS userId,
              title AS title,
              summary AS summary,
              cover_url AS coverUrl,
              content AS content,
              status AS status,
              published_at AS publishedAt,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM article
            WHERE user_id = #{userId}
              AND status <> 3
            ORDER BY updated_at DESC
            LIMIT #{offset}, #{size}
            """)
    List<ArticleEntity> selectPageByUserId(@Param("userId") Long userId,
                                           @Param("offset") int offset,
                                           @Param("size") int size);

    @Select("""
            SELECT COUNT(1)
            FROM article
            WHERE user_id = #{userId}
              AND status <> 3
            """)
    long countByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT
              id AS id,
              user_id AS userId,
              title AS title,
              summary AS summary,
              cover_url AS coverUrl,
              content AS content,
              status AS status,
              published_at AS publishedAt,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM article
            WHERE status = 1
              AND (#{authorUserId} IS NULL OR user_id = #{authorUserId})
            ORDER BY published_at DESC, id DESC
            LIMIT #{offset}, #{size}
            """)
    List<ArticleEntity> selectPublishedPage(@Param("authorUserId") Long authorUserId,
                                            @Param("offset") int offset,
                                            @Param("size") int size);

    @Select("""
            SELECT COUNT(1)
            FROM article
            WHERE status = 1
              AND (#{authorUserId} IS NULL OR user_id = #{authorUserId})
            """)
    long countPublished(@Param("authorUserId") Long authorUserId);
}
