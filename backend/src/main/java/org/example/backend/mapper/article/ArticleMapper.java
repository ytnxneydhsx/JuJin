package org.example.backend.mapper.article;

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
            INSERT INTO article(user_id, title, summary, cover_url, status, published_at)
            VALUES(#{userId}, #{title}, #{summary}, #{coverUrl}, #{status}, NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ArticleEntity entity);

    @Update("""
            UPDATE article
            SET title = #{title},
                summary = #{summary},
                cover_url = #{coverUrl},
                status = COALESCE(#{status}, status),
                updated_at = NOW()
            WHERE id = #{articleId}
              AND user_id = #{userId}
              AND status IN (1, 2)
            """)
    int updateByIdAndUserId(@Param("articleId") Long articleId,
                            @Param("userId") Long userId,
                            @Param("title") String title,
                            @Param("summary") String summary,
                            @Param("coverUrl") String coverUrl,
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
              a.id AS id,
              a.user_id AS userId,
              a.title AS title,
              a.summary AS summary,
              a.cover_url AS coverUrl,
              c.content AS content,
              a.status AS status,
              COALESCE(s.like_count, 0) AS likeCount,
              COALESCE(s.favorite_count, 0) AS favoriteCount,
              COALESCE(s.view_count, 0) AS viewCount,
              a.published_at AS publishedAt,
              a.created_at AS createdAt,
              a.updated_at AS updatedAt
            FROM article a
            LEFT JOIN article_content c ON c.article_id = a.id
            LEFT JOIN article_stats s ON s.article_id = a.id
            WHERE a.id = #{articleId}
              AND a.user_id = #{userId}
              AND a.status <> 3
            """)
    ArticleEntity selectByIdAndUserId(@Param("articleId") Long articleId,
                                      @Param("userId") Long userId);

    @Select("""
            SELECT
              a.id AS id,
              a.user_id AS userId,
              a.title AS title,
              a.summary AS summary,
              a.cover_url AS coverUrl,
              c.content AS content,
              a.status AS status,
              COALESCE(s.like_count, 0) AS likeCount,
              COALESCE(s.favorite_count, 0) AS favoriteCount,
              COALESCE(s.view_count, 0) AS viewCount,
              a.published_at AS publishedAt,
              a.created_at AS createdAt,
              a.updated_at AS updatedAt
            FROM article a
            LEFT JOIN article_content c ON c.article_id = a.id
            LEFT JOIN article_stats s ON s.article_id = a.id
            WHERE a.id = #{articleId}
              AND a.status = 1
            """)
    ArticleEntity selectPublishedById(@Param("articleId") Long articleId);

    @Select("""
            SELECT
              a.id AS id,
              a.user_id AS userId,
              a.title AS title,
              a.summary AS summary,
              a.cover_url AS coverUrl,
              a.status AS status,
              COALESCE(s.like_count, 0) AS likeCount,
              COALESCE(s.favorite_count, 0) AS favoriteCount,
              COALESCE(s.view_count, 0) AS viewCount,
              a.published_at AS publishedAt,
              a.created_at AS createdAt,
              a.updated_at AS updatedAt
            FROM article a
            LEFT JOIN article_stats s ON s.article_id = a.id
            WHERE a.user_id = #{userId}
              AND a.status <> 3
            ORDER BY a.updated_at DESC
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

    @Select({
            "<script>",
            "SELECT",
            "  a.id AS id,",
            "  a.user_id AS userId,",
            "  a.title AS title,",
            "  a.summary AS summary,",
            "  a.cover_url AS coverUrl,",
            "  a.status AS status,",
            "  COALESCE(s.like_count, 0) AS likeCount,",
            "  COALESCE(s.favorite_count, 0) AS favoriteCount,",
            "  COALESCE(s.view_count, 0) AS viewCount,",
            "  a.published_at AS publishedAt,",
            "  a.created_at AS createdAt,",
            "  a.updated_at AS updatedAt",
            "FROM article a",
            "LEFT JOIN article_stats s ON s.article_id = a.id",
            "WHERE a.status = 1",
            "  AND (#{authorUserId} IS NULL OR a.user_id = #{authorUserId})",
            "ORDER BY",
            "  <choose>",
            "    <when test='sortBy == \"viewCount\"'>COALESCE(s.view_count, 0)</when>",
            "    <otherwise>a.published_at</otherwise>",
            "  </choose>",
            "  <choose>",
            "    <when test='sortOrder == \"asc\"'>ASC</when>",
            "    <otherwise>DESC</otherwise>",
            "  </choose>,",
            "  a.id",
            "  <choose>",
            "    <when test='sortOrder == \"asc\"'>ASC</when>",
            "    <otherwise>DESC</otherwise>",
            "  </choose>",
            "LIMIT #{offset}, #{size}",
            "</script>"
    })
    List<ArticleEntity> selectPublishedPage(@Param("authorUserId") Long authorUserId,
                                            @Param("offset") int offset,
                                            @Param("size") int size,
                                            @Param("sortBy") String sortBy,
                                            @Param("sortOrder") String sortOrder);

    @Select("""
            SELECT COUNT(1)
            FROM article
            WHERE status = 1
              AND (#{authorUserId} IS NULL OR user_id = #{authorUserId})
            """)
    long countPublished(@Param("authorUserId") Long authorUserId);

    @Select("""
            SELECT COUNT(1)
            FROM article
            WHERE id = #{articleId}
              AND status = 1
            """)
    int countPublishedById(@Param("articleId") Long articleId);

    @Select("""
            SELECT
              a.id AS id,
              COALESCE(s.like_count, 0) AS likeCount,
              COALESCE(s.favorite_count, 0) AS favoriteCount,
              COALESCE(s.view_count, 0) AS viewCount
            FROM article a
            LEFT JOIN article_stats s ON s.article_id = a.id
            WHERE a.id = #{articleId}
              AND a.status = 1
            """)
    ArticleEntity selectInteractionStatsById(@Param("articleId") Long articleId);

    @Update("""
            UPDATE article_stats s
            JOIN article a ON a.id = s.article_id
            SET s.view_count = s.view_count + 1
            WHERE a.id = #{articleId}
              AND a.status = 1
            """)
    int incrementViewCountById(@Param("articleId") Long articleId);

    @Update("""
            UPDATE article_stats s
            JOIN article a ON a.id = s.article_id
            SET s.view_count = #{viewCount}
            WHERE a.id = #{articleId}
              AND a.status = 1
            """)
    int updateViewCountById(@Param("articleId") Long articleId,
                            @Param("viewCount") Long viewCount);

    @Update("""
            UPDATE article_stats s
            JOIN article a ON a.id = s.article_id
            SET s.like_count = s.like_count + 1
            WHERE a.id = #{articleId}
              AND a.status = 1
            """)
    int incrementLikeCountById(@Param("articleId") Long articleId);

    @Update("""
            UPDATE article_stats s
            JOIN article a ON a.id = s.article_id
            SET s.like_count = #{likeCount}
            WHERE a.id = #{articleId}
              AND a.status <> 3
            """)
    int updateLikeCountById(@Param("articleId") Long articleId,
                            @Param("likeCount") Long likeCount);

    @Update("""
            UPDATE article_stats s
            JOIN article a ON a.id = s.article_id
            SET s.like_count = s.like_count - 1
            WHERE a.id = #{articleId}
              AND a.status = 1
              AND s.like_count > 0
            """)
    int decrementLikeCountById(@Param("articleId") Long articleId);

    @Update("""
            UPDATE article_stats s
            JOIN article a ON a.id = s.article_id
            SET s.favorite_count = s.favorite_count + 1
            WHERE a.id = #{articleId}
              AND a.status = 1
            """)
    int incrementFavoriteCountById(@Param("articleId") Long articleId);

    @Update("""
            UPDATE article_stats s
            JOIN article a ON a.id = s.article_id
            SET s.favorite_count = s.favorite_count - 1
            WHERE a.id = #{articleId}
              AND a.status = 1
              AND s.favorite_count > 0
            """)
    int decrementFavoriteCountById(@Param("articleId") Long articleId);

    @Insert("""
            INSERT INTO article_content(article_id, content)
            VALUES(#{articleId}, #{content})
            ON DUPLICATE KEY UPDATE
              content = VALUES(content),
              updated_at = CURRENT_TIMESTAMP
            """)
    int upsertContentByArticleId(@Param("articleId") Long articleId,
                                 @Param("content") String content);

    @Insert("""
            INSERT INTO article_stats(article_id, like_count, favorite_count, view_count)
            VALUES(#{articleId}, #{likeCount}, #{favoriteCount}, #{viewCount})
            ON DUPLICATE KEY UPDATE
              article_id = article_id
            """)
    int ensureStatsByArticleId(@Param("articleId") Long articleId,
                               @Param("likeCount") Long likeCount,
                               @Param("favoriteCount") Long favoriteCount,
                               @Param("viewCount") Long viewCount);
}
