package org.example.backend.service.base;

import org.example.backend.model.dto.user.LoginDTO;
import org.example.backend.model.dto.user.RegisterDTO;
import org.example.backend.model.dto.user.UpdatePasswordDTO;
import org.example.backend.model.vo.UserPublicProfileVO;
import org.example.backend.model.vo.UserRegisterVO;

public interface UserBaseService {

    UserRegisterVO register(RegisterDTO dto);

    UserRegisterVO login(LoginDTO dto);

    void updateSign(Long userId, String sign);

    void updateAvatar(Long userId, String avatarUrl);

    void updateName(Long userId, String name);

    void updatePassword(Long userId, UpdatePasswordDTO dto);

    UserPublicProfileVO getPublicProfile(Long userId);
}
