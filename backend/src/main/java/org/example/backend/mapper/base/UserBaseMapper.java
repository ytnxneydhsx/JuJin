package org.example.backend.mapper.base;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserBaseMapper {

    @Select("SELECT COUNT(1) FROM user_base WHERE account = #{account}")
    int countByAccount(@Param("account") String account);

    @Insert("""
            INSERT INTO user_base(account, password_hash, status)
            VALUES(#{account}, #{passwordHash}, #{status})
            """)
    int insertUserBase(@Param("account") String account,
                       @Param("passwordHash") String passwordHash,
                       @Param("status") int status);

    @Select("SELECT id FROM user_base WHERE account = #{account}")
    Long selectIdByAccount(@Param("account") String account);

    @Select("SELECT status FROM user_base WHERE account = #{account}")
    Integer selectStatusByAccount(@Param("account") String account);

    @Select("SELECT account FROM user_base WHERE id = #{userId}")
    String selectAccountById(@Param("userId") Long userId);

    @Select("SELECT password_hash FROM user_base WHERE account = #{account}")
    String selectPasswordHashByAccount(@Param("account") String account);

    @Select("SELECT password_hash FROM user_base WHERE id = #{userId}")
    String selectPasswordHashByUserId(@Param("userId") Long userId);

    @Update("""
            UPDATE user_base
            SET password_hash = #{passwordHash}
            WHERE account = #{account}
            """)
    int updatePasswordHashByAccount(@Param("account") String account,
                                    @Param("passwordHash") String passwordHash);

    @Update("""
            UPDATE user_base
            SET password_hash = #{passwordHash}
            WHERE id = #{userId}
            """)
    int updatePasswordHashByUserId(@Param("userId") Long userId,
                                   @Param("passwordHash") String passwordHash);
}
