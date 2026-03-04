package org.example.backend.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.backend.common.response.Result;
import org.example.backend.config.LoginUserPrincipal;
import org.example.backend.config.SessionKeys;
import org.example.backend.exception.BizException;
import org.example.backend.model.dto.user.LoginDTO;
import org.example.backend.model.dto.user.RegisterDTO;
import org.example.backend.model.dto.user.UpdateAvatarDTO;
import org.example.backend.model.dto.user.UpdateNameDTO;
import org.example.backend.model.dto.user.UpdatePasswordDTO;
import org.example.backend.model.dto.user.UpdateSignDTO;
import org.example.backend.model.vo.UserRegisterVO;
import org.example.backend.service.base.UserBaseService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/user")
@RequiredArgsConstructor
public class MeUserController {

    private final UserBaseService userBaseService;

    @PostMapping("/register")
    public Result<UserRegisterVO> register(@Valid @RequestBody RegisterDTO dto, HttpSession session) {
        UserRegisterVO registerVO = userBaseService.register(dto);
        session.setAttribute(SessionKeys.LOGIN_USER_ID, registerVO.getUserId());
        session.setAttribute(SessionKeys.LOGIN_ACCOUNT, registerVO.getAccount());
        return Result.success("Registered successfully", registerVO);
    }

    @PostMapping("/login")
    public Result<UserRegisterVO> login(@Valid @RequestBody LoginDTO dto, HttpSession session) {
        UserRegisterVO loginVO = userBaseService.login(dto);
        session.setAttribute(SessionKeys.LOGIN_USER_ID, loginVO.getUserId());
        session.setAttribute(SessionKeys.LOGIN_ACCOUNT, loginVO.getAccount());
        return Result.success("Login successfully", loginVO);
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpSession session) {
        session.invalidate();
        return Result.success("Logged out successfully", null);
    }

    @PutMapping("/profile/sign")
    public Result<Void> updateSign(@Valid @RequestBody UpdateSignDTO dto, Authentication authentication) {
        Long userId = requireLoginUserId(authentication);
        userBaseService.updateSign(userId, dto.getSign().trim());
        return Result.success("Sign updated successfully", null);
    }

    @PutMapping("/profile/avatar")
    public Result<Void> updateAvatar(@Valid @RequestBody UpdateAvatarDTO dto, Authentication authentication) {
        Long userId = requireLoginUserId(authentication);
        userBaseService.updateAvatar(userId, dto.getAvatarUrl().trim());
        return Result.success("Avatar updated successfully", null);
    }

    @PutMapping("/profile/name")
    public Result<Void> updateName(@Valid @RequestBody UpdateNameDTO dto, Authentication authentication) {
        Long userId = requireLoginUserId(authentication);
        userBaseService.updateName(userId, dto.getName().trim());
        return Result.success("Name updated successfully", null);
    }

    @PutMapping("/password")
    public Result<Void> updatePassword(@Valid @RequestBody UpdatePasswordDTO dto,
                                       Authentication authentication) {
        Long userId = requireLoginUserId(authentication);
        userBaseService.updatePassword(userId, dto);
        return Result.success("Password updated successfully", null);
    }

    private Long requireLoginUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUserPrincipal principal)) {
            throw new BizException("UNAUTHORIZED", "Please login first");
        }
        if (principal.getUserId() == null) {
            throw new BizException("UNAUTHORIZED", "Please login first");
        }
        return principal.getUserId();
    }
}
