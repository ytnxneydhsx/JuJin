package org.example.backend.mapper.user;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.backend.model.dto.user.UserLoginSource;
import org.example.backend.model.entity.UserAccountEntity;

@Mapper
public interface UserAccountMapper {

    @Insert("""
            INSERT INTO user_base(account, password_hash, status)
            VALUES(#{account}, #{passwordHash}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserAccountEntity entity);

    @Select("""
            SELECT
              id AS userId,
              account AS account,
              password_hash AS passwordHash,
              status AS status
            FROM user_base
            WHERE account = #{account}
            """)
    UserLoginSource selectLoginSourceByAccount(@Param("account") String account);

    @Select("SELECT password_hash FROM user_base WHERE id = #{userId}")
    String selectPasswordHashByUserId(@Param("userId") Long userId);

    @Update("""
            UPDATE user_base
            SET password_hash = #{passwordHash}
            WHERE id = #{userId}
            """)
    int updatePasswordHashByUserId(@Param("userId") Long userId,
                                   @Param("passwordHash") String passwordHash);
}
