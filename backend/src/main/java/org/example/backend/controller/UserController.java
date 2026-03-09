package org.example.backend.controller;

import lombok.RequiredArgsConstructor;
import org.example.backend.common.response.Result;
import org.example.backend.model.vo.UserPublicProfileVO;
import org.example.backend.service.core.user.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{userId}")
    public Result<UserPublicProfileVO> getUserById(@PathVariable("userId") Long userId) {
        UserPublicProfileVO profile = userService.getPublicProfile(userId);
        return Result.success(profile);
    }
}

