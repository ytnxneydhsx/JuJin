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
              like_count AS likeCount,
              favorite_count AS favoriteCount,
              view_count AS viewCount,
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
              like_count AS likeCount,
              favorite_count AS favoriteCount,
              view_count AS viewCount,
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
              like_count AS likeCount,
              favorite_count AS favoriteCount,
              view_count AS viewCount,
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

    @Select({
            "<script>",
            "SELECT",
            "  id AS id,",
            "  user_id AS userId,",
            "  title AS title,",
            "  summary AS summary,",
            "  cover_url AS coverUrl,",
            "  content AS content,",
            "  status AS status,",
            "  like_count AS likeCount,",
            "  favorite_count AS favoriteCount,",
            "  view_count AS viewCount,",
            "  published_at AS publishedAt,",
            "  created_at AS createdAt,",
            "  updated_at AS updatedAt",
            "FROM article",
            "WHERE status = 1",
            "  AND (#{authorUserId} IS NULL OR user_id = #{authorUserId})",
            "ORDER BY",
            "  <choose>",
            "    <when test='sortBy == \"viewCount\"'>view_count</when>",
            "    <otherwise>published_at</otherwise>",
            "  </choose>",
            "  <choose>",
            "    <when test='sortOrder == \"asc\"'>ASC</when>",
            "    <otherwise>DESC</otherwise>",
            "  </choose>,",
            "  id",
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
              id AS id,
              like_count AS likeCount,
              favorite_count AS favoriteCount,
              view_count AS viewCount
            FROM article
            WHERE id = #{articleId}
              AND status = 1
            """)
    ArticleEntity selectInteractionStatsById(@Param("articleId") Long articleId);

    @Update("""
            UPDATE article
            SET view_count = view_count + 1
            WHERE id = #{articleId}
              AND status = 1
            """)
    int incrementViewCountById(@Param("articleId") Long articleId);

    @Update("""
            UPDATE article
            SET view_count = #{viewCount}
            WHERE id = #{articleId}
              AND status = 1
            """)
    int updateViewCountById(@Param("articleId") Long articleId,
                            @Param("viewCount") Long viewCount);

    @Update("""
            UPDATE article
            SET like_count = like_count + 1
            WHERE id = #{articleId}
              AND status = 1
            """)
    int incrementLikeCountById(@Param("articleId") Long articleId);

    @Update("""
            UPDATE article
            SET like_count = #{likeCount}
            WHERE id = #{articleId}
              AND status <> 3
            """)
    int updateLikeCountById(@Param("articleId") Long articleId,
                            @Param("likeCount") Long likeCount);

    @Update("""
            UPDATE article
            SET like_count = like_count - 1
            WHERE id = #{articleId}
              AND status = 1
              AND like_count > 0
            """)
    int decrementLikeCountById(@Param("articleId") Long articleId);

    @Update("""
            UPDATE article
            SET favorite_count = favorite_count + 1
            WHERE id = #{articleId}
              AND status = 1
            """)
    int incrementFavoriteCountById(@Param("articleId") Long articleId);

    @Update("""
            UPDATE article
            SET favorite_count = favorite_count - 1
            WHERE id = #{articleId}
              AND status = 1
              AND favorite_count > 0
            """)
    int decrementFavoriteCountById(@Param("articleId") Long articleId);
}
