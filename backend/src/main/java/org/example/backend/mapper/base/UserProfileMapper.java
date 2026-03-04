package org.example.backend.mapper.base;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.backend.model.vo.UserPublicProfileVO;

@Mapper
public interface UserProfileMapper {

    @Insert("""
            INSERT INTO user_profile(account, name, avatar_url, `sign`)
            VALUES(#{account}, #{name}, #{avatarUrl}, #{sign})
            """)
    int insertUserProfile(@Param("account") String account,
                          @Param("name") String name,
                          @Param("avatarUrl") String avatarUrl,
                          @Param("sign") String sign);

    @Update("""
            UPDATE user_profile
            SET `sign` = #{sign}
            WHERE account = #{account}
            """)
    int updateSignByAccount(@Param("account") String account,
                            @Param("sign") String sign);

    @Update("""
            UPDATE user_profile
            SET avatar_url = #{avatarUrl}
            WHERE account = #{account}
            """)
    int updateAvatarUrlByAccount(@Param("account") String account,
                                 @Param("avatarUrl") String avatarUrl);

    @Update("""
            UPDATE user_profile
            SET name = #{name}
            WHERE account = #{account}
            """)
    int updateNameByAccount(@Param("account") String account,
                            @Param("name") String name);

    @Update("""
            UPDATE user_profile up
            JOIN user_base ub ON up.account = ub.account
            SET up.`sign` = #{sign}
            WHERE ub.id = #{userId}
            """)
    int updateSignByUserId(@Param("userId") Long userId,
                           @Param("sign") String sign);

    @Update("""
            UPDATE user_profile up
            JOIN user_base ub ON up.account = ub.account
            SET up.avatar_url = #{avatarUrl}
            WHERE ub.id = #{userId}
            """)
    int updateAvatarUrlByUserId(@Param("userId") Long userId,
                                @Param("avatarUrl") String avatarUrl);

    @Update("""
            UPDATE user_profile up
            JOIN user_base ub ON up.account = ub.account
            SET up.name = #{name}
            WHERE ub.id = #{userId}
            """)
    int updateNameByUserId(@Param("userId") Long userId,
                           @Param("name") String name);

    @Select("""
            SELECT
              ub.id AS userId,
              COALESCE(up.name, ub.account) AS name,
              up.avatar_url AS avatarUrl,
              up.`sign` AS sign
            FROM user_base ub
            LEFT JOIN user_profile up ON up.account = ub.account
            WHERE ub.id = #{userId}
              AND ub.status = 1
            """)
    UserPublicProfileVO selectPublicProfileByUserId(@Param("userId") Long userId);
}
