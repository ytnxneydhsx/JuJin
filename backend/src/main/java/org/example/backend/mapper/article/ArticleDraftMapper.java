package org.example.backend.mapper.article;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.backend.model.entity.ArticleDraftEntity;

import java.util.List;

@Mapper
public interface ArticleDraftMapper {

    @Insert("""
            INSERT INTO article_draft(user_id, article_id, status, title, summary, cover_url, content)
            VALUES(#{userId}, #{articleId}, #{status}, #{title}, #{summary}, #{coverUrl}, #{content})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ArticleDraftEntity entity);

    @Update("""
            UPDATE article_draft
            SET article_id = #{articleId},
                title = #{title},
                summary = #{summary},
                cover_url = #{coverUrl},
                content = #{content}
            WHERE id = #{draftId}
              AND user_id = #{userId}
              AND status = #{draftStatus}
            """)
    int updateByIdAndUserId(@Param("draftId") Long draftId,
                            @Param("userId") Long userId,
                            @Param("articleId") Long articleId,
                            @Param("draftStatus") Integer draftStatus,
                            @Param("title") String title,
                            @Param("summary") String summary,
                            @Param("coverUrl") String coverUrl,
                            @Param("content") String content);

    @Update("""
            UPDATE article_draft
            SET article_id = #{articleId},
                status = #{publishedStatus}
            WHERE id = #{draftId}
              AND user_id = #{userId}
              AND status = #{draftStatus}
            """)
    int markPublishedByIdAndUserId(@Param("draftId") Long draftId,
                                   @Param("userId") Long userId,
                                   @Param("articleId") Long articleId,
                                   @Param("publishedStatus") Integer publishedStatus,
                                   @Param("draftStatus") Integer draftStatus);

    @Update("""
            UPDATE article_draft
            SET status = #{deletedStatus}
            WHERE id = #{draftId}
              AND user_id = #{userId}
              AND status = #{draftStatus}
            """)
    int softDeleteByIdAndUserId(@Param("draftId") Long draftId,
                                @Param("userId") Long userId,
                                @Param("deletedStatus") Integer deletedStatus,
                                @Param("draftStatus") Integer draftStatus);

    @Select("""
            SELECT
              id AS id,
              user_id AS userId,
              article_id AS articleId,
              status AS status,
              title AS title,
              summary AS summary,
              cover_url AS coverUrl,
              content AS content,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM article_draft
            WHERE id = #{draftId}
              AND user_id = #{userId}
            """)
    ArticleDraftEntity selectByIdAndUserId(@Param("draftId") Long draftId,
                                           @Param("userId") Long userId);

    @Select("""
            SELECT
              id AS id,
              user_id AS userId,
              article_id AS articleId,
              status AS status,
              title AS title,
              summary AS summary,
              cover_url AS coverUrl,
              content AS content,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM article_draft
            WHERE user_id = #{userId}
              AND status = #{draftStatus}
            ORDER BY updated_at DESC, id DESC
            LIMIT #{offset}, #{size}
            """)
    List<ArticleDraftEntity> selectPageByUserId(@Param("userId") Long userId,
                                                @Param("draftStatus") Integer draftStatus,
                                                @Param("offset") int offset,
                                                @Param("size") int size);

    @Select("""
            SELECT COUNT(1)
            FROM article_draft
            WHERE user_id = #{userId}
              AND status = #{draftStatus}
            """)
    long countByUserId(@Param("userId") Long userId,
                       @Param("draftStatus") Integer draftStatus);
}
