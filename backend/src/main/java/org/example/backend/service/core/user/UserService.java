package org.example.backend.service.core.user;

import org.example.backend.model.dto.user.LoginDTO;
import org.example.backend.model.dto.user.RegisterDTO;
import org.example.backend.model.dto.user.UpdatePasswordDTO;
import org.example.backend.model.vo.UserPublicProfileVO;
import org.example.backend.model.vo.UserRegisterVO;

public interface UserService {

    UserRegisterVO register(RegisterDTO dto);

    UserRegisterVO login(LoginDTO dto);

    void updateSign(Long userId, String account, String sign);

    void updateAvatar(Long userId, String account, String avatarUrl);

    void updateName(Long userId, String account, String name);

    void updatePassword(Long userId, UpdatePasswordDTO dto);

    UserPublicProfileVO getPublicProfile(Long userId);
}
