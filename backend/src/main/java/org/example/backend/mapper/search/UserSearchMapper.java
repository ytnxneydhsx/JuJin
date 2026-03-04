package org.example.backend.mapper.search;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.backend.model.dto.UserSearchSource;

import java.util.List;

@Mapper
public interface UserSearchMapper {

    @Select("""
            SELECT
              ub.id AS id,
              ub.account AS account,
              COALESCE(up.name, ub.account) AS name,
              ub.status AS status,
              up.avatar_url AS avatarUrl,
              up.`sign` AS sign,
              CASE
                WHEN up.updated_at IS NULL THEN ub.updated_at
                ELSE GREATEST(ub.updated_at, up.updated_at)
              END AS updatedAt
            FROM user_base ub
            LEFT JOIN user_profile up ON up.account = ub.account
            ORDER BY ub.id ASC
            """)
    List<UserSearchSource> selectAllForSearch();

    @Select("""
            SELECT
              ub.id AS id,
              ub.account AS account,
              COALESCE(up.name, ub.account) AS name,
              ub.status AS status,
              up.avatar_url AS avatarUrl,
              up.`sign` AS sign,
              CASE
                WHEN up.updated_at IS NULL THEN ub.updated_at
                ELSE GREATEST(ub.updated_at, up.updated_at)
              END AS updatedAt
            FROM user_base ub
            LEFT JOIN user_profile up ON up.account = ub.account
            WHERE ub.account = #{account}
            """)
    UserSearchSource selectByAccountForSearch(@Param("account") String account);
}
