package org.example.backend.mapper.interaction;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CommentLikeMapper {

    @Select("""
            SELECT status
            FROM comment_like
            WHERE user_id = #{userId}
              AND comment_id = #{commentId}
            """)
    Integer selectStatusByUserIdAndCommentId(@Param("userId") Long userId,
                                             @Param("commentId") Long commentId);

    @Insert("""
            INSERT INTO comment_like(user_id, comment_id, status)
            VALUES(#{userId}, #{commentId}, #{status})
            """)
    int insert(@Param("userId") Long userId,
               @Param("commentId") Long commentId,
               @Param("status") Integer status);

    @Update("""
            UPDATE comment_like
            SET status = #{newStatus}
            WHERE user_id = #{userId}
              AND comment_id = #{commentId}
              AND status = #{expectedStatus}
            """)
    int updateStatusByUserIdAndCommentId(@Param("userId") Long userId,
                                         @Param("commentId") Long commentId,
                                         @Param("newStatus") Integer newStatus,
                                         @Param("expectedStatus") Integer expectedStatus);

    @Select({
            "<script>",
            "SELECT comment_id",
            "FROM comment_like",
            "WHERE user_id = #{userId}",
            "  AND status = #{status}",
            "  AND comment_id IN",
            "  <foreach collection='commentIds' item='commentId' open='(' separator=',' close=')'>",
            "    #{commentId}",
            "  </foreach>",
            "</script>"
    })
    List<Long> selectCommentIdsByUserIdAndStatus(@Param("userId") Long userId,
                                                  @Param("status") Integer status,
                                                  @Param("commentIds") List<Long> commentIds);
}
