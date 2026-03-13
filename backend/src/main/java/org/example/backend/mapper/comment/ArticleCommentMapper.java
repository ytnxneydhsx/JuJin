package org.example.backend.mapper.comment;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.backend.model.dto.comment.CommentChildCountDTO;
import org.example.backend.model.entity.ArticleCommentEntity;

import java.util.List;

@Mapper
public interface ArticleCommentMapper {

    @Insert("""
            INSERT INTO article_comment(article_id, user_id, root_id, parent_id, reply_to_user_id, content, status)
            VALUES(#{articleId}, #{userId}, #{rootId}, #{parentId}, #{replyToUserId}, #{content}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ArticleCommentEntity entity);

    @Update("""
            UPDATE article_comment
            SET root_id = #{rootId},
                updated_at = NOW()
            WHERE id = #{commentId}
            """)
    int updateRootIdById(@Param("commentId") Long commentId,
                         @Param("rootId") Long rootId);

    @Update("""
            UPDATE article_comment
            SET status = #{newStatus},
                updated_at = NOW()
            WHERE id = #{commentId}
              AND user_id = #{userId}
              AND status = #{expectedStatus}
            """)
    int updateStatusByIdAndUserId(@Param("commentId") Long commentId,
                                  @Param("userId") Long userId,
                                  @Param("newStatus") Integer newStatus,
                                  @Param("expectedStatus") Integer expectedStatus);

    @Select("""
            SELECT
              id AS id,
              article_id AS articleId,
              user_id AS userId,
              root_id AS rootId,
              parent_id AS parentId,
              reply_to_user_id AS replyToUserId,
              content AS content,
              status AS status,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM article_comment
            WHERE id = #{commentId}
              AND article_id = #{articleId}
              AND status = #{status}
            """)
    ArticleCommentEntity selectByIdAndArticleId(@Param("commentId") Long commentId,
                                                @Param("articleId") Long articleId,
                                                @Param("status") Integer status);

    @Select("""
            SELECT COUNT(1)
            FROM article_comment
            WHERE id = #{commentId}
              AND status = #{status}
            """)
    int countByIdAndStatus(@Param("commentId") Long commentId,
                           @Param("status") Integer status);

    @Select("""
            SELECT
              id AS id,
              article_id AS articleId,
              user_id AS userId,
              root_id AS rootId,
              parent_id AS parentId,
              reply_to_user_id AS replyToUserId,
              content AS content,
              status AS status,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM article_comment
            WHERE article_id = #{articleId}
              AND parent_id IS NULL
              AND status = #{status}
            ORDER BY created_at DESC, id DESC
            LIMIT #{offset}, #{size}
            """)
    List<ArticleCommentEntity> selectRootPageByArticleId(@Param("articleId") Long articleId,
                                                         @Param("status") Integer status,
                                                         @Param("offset") int offset,
                                                         @Param("size") int size);

    @Select("""
            SELECT COUNT(1)
            FROM article_comment
            WHERE article_id = #{articleId}
              AND parent_id IS NULL
              AND status = #{status}
            """)
    long countRootByArticleId(@Param("articleId") Long articleId,
                              @Param("status") Integer status);

    @Select("""
            SELECT
              id AS id,
              article_id AS articleId,
              user_id AS userId,
              root_id AS rootId,
              parent_id AS parentId,
              reply_to_user_id AS replyToUserId,
              content AS content,
              status AS status,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM article_comment
            WHERE article_id = #{articleId}
              AND parent_id = #{parentId}
              AND status = #{status}
            ORDER BY created_at ASC, id ASC
            LIMIT #{offset}, #{size}
            """)
    List<ArticleCommentEntity> selectChildPageByArticleIdAndParentId(@Param("articleId") Long articleId,
                                                                     @Param("parentId") Long parentId,
                                                                     @Param("status") Integer status,
                                                                     @Param("offset") int offset,
                                                                     @Param("size") int size);

    @Select("""
            SELECT COUNT(1)
            FROM article_comment
            WHERE article_id = #{articleId}
              AND parent_id = #{parentId}
              AND status = #{status}
            """)
    long countChildByArticleIdAndParentId(@Param("articleId") Long articleId,
                                          @Param("parentId") Long parentId,
                                          @Param("status") Integer status);

    @Select({
            "<script>",
            "SELECT",
            "  parent_id AS parentId,",
            "  COUNT(1) AS childCount",
            "FROM article_comment",
            "WHERE article_id = #{articleId}",
            "  AND status = #{status}",
            "  AND parent_id IN",
            "  <foreach collection='parentIds' item='parentId' open='(' separator=',' close=')'>",
            "    #{parentId}",
            "  </foreach>",
            "GROUP BY parent_id",
            "</script>"
    })
    List<CommentChildCountDTO> selectChildCountsByArticleIdAndParentIds(@Param("articleId") Long articleId,
                                                                        @Param("parentIds") List<Long> parentIds,
                                                                        @Param("status") Integer status);

    @Select({
            "<script>",
            "SELECT",
            "  id AS id,",
            "  article_id AS articleId,",
            "  user_id AS userId,",
            "  root_id AS rootId,",
            "  parent_id AS parentId,",
            "  reply_to_user_id AS replyToUserId,",
            "  content AS content,",
            "  status AS status,",
            "  created_at AS createdAt,",
            "  updated_at AS updatedAt",
            "FROM (",
            "  SELECT",
            "    id,",
            "    article_id,",
            "    user_id,",
            "    root_id,",
            "    parent_id,",
            "    reply_to_user_id,",
            "    content,",
            "    status,",
            "    created_at,",
            "    updated_at,",
            "    ROW_NUMBER() OVER (PARTITION BY parent_id ORDER BY created_at ASC, id ASC) AS rn",
            "  FROM article_comment",
            "  WHERE article_id = #{articleId}",
            "    AND status = #{status}",
            "    AND parent_id IN",
            "    <foreach collection='parentIds' item='parentId' open='(' separator=',' close=')'>",
            "      #{parentId}",
            "    </foreach>",
            ") ranked",
            "WHERE rn &lt;= #{limitPerParent}",
            "ORDER BY parentId ASC, createdAt ASC, id ASC",
            "</script>"
    })
    List<ArticleCommentEntity> selectChildPreviewByArticleIdAndParentIds(@Param("articleId") Long articleId,
                                                                         @Param("parentIds") List<Long> parentIds,
                                                                         @Param("status") Integer status,
                                                                         @Param("limitPerParent") int limitPerParent);
}
