package org.example.backend.mapper.interaction;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ArticleLikeMapper {

    @Select("""
            SELECT status
            FROM article_like
            WHERE user_id = #{userId}
              AND article_id = #{articleId}
            """)
    Integer selectStatusByUserIdAndArticleId(@Param("userId") Long userId,
                                             @Param("articleId") Long articleId);

    @Insert("""
            INSERT INTO article_like(user_id, article_id, status)
            VALUES(#{userId}, #{articleId}, #{status})
            """)
    int insert(@Param("userId") Long userId,
               @Param("articleId") Long articleId,
               @Param("status") Integer status);

    @Update("""
            UPDATE article_like
            SET status = #{newStatus}
            WHERE user_id = #{userId}
              AND article_id = #{articleId}
              AND status = #{expectedStatus}
            """)
    int updateStatusByUserIdAndArticleId(@Param("userId") Long userId,
                                         @Param("articleId") Long articleId,
                                         @Param("newStatus") Integer newStatus,
                                         @Param("expectedStatus") Integer expectedStatus);

    @Select({
            "<script>",
            "SELECT article_id",
            "FROM article_like",
            "WHERE user_id = #{userId}",
            "  AND status = #{status}",
            "  AND article_id IN",
            "  <foreach collection='articleIds' item='articleId' open='(' separator=',' close=')'>",
            "    #{articleId}",
            "  </foreach>",
            "</script>"
    })
    List<Long> selectArticleIdsByUserIdAndStatus(@Param("userId") Long userId,
                                                  @Param("status") Integer status,
                                                  @Param("articleIds") List<Long> articleIds);

    @Select("""
            SELECT user_id
            FROM article_like
            WHERE article_id = #{articleId}
              AND status = #{status}
            """)
    List<Long> selectActiveUserIdsByArticleId(@Param("articleId") Long articleId,
                                              @Param("status") Integer status);

    @Update("""
            UPDATE article_like
            SET status = #{newStatus},
                updated_at = NOW()
            WHERE article_id = #{articleId}
              AND status = #{expectedStatus}
            """)
    int cancelAllActiveByArticleId(@Param("articleId") Long articleId,
                                   @Param("newStatus") Integer newStatus,
                                   @Param("expectedStatus") Integer expectedStatus);

    @Insert({
            "<script>",
            "INSERT INTO article_like(user_id, article_id, status) VALUES",
            "<foreach collection='userIds' item='userId' separator=','>",
            "(#{userId}, #{articleId}, #{status})",
            "</foreach>",
            "ON DUPLICATE KEY UPDATE",
            "status = VALUES(status),",
            "updated_at = NOW()",
            "</script>"
    })
    int batchUpsertStatusByArticleId(@Param("articleId") Long articleId,
                                     @Param("userIds") List<Long> userIds,
                                     @Param("status") Integer status);
}
