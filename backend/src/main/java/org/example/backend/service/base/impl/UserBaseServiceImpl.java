package org.example.backend.service.base.impl;

import lombok.RequiredArgsConstructor;
import org.example.backend.exception.BizException;
import org.example.backend.mapper.base.UserBaseMapper;
import org.example.backend.mapper.base.UserProfileMapper;
import org.example.backend.model.dto.user.LoginDTO;
import org.example.backend.model.dto.user.RegisterDTO;
import org.example.backend.model.dto.user.UpdatePasswordDTO;
import org.example.backend.model.vo.UserPublicProfileVO;
import org.example.backend.model.vo.UserRegisterVO;
import org.example.backend.service.base.UserBaseService;
import org.example.backend.service.search.UserSearchService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserBaseServiceImpl implements UserBaseService {

    private static final int ACTIVE_STATUS = 1;

    private final UserBaseMapper userBaseMapper;
    private final UserProfileMapper userProfileMapper;
    private final UserSearchService userSearchService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public UserRegisterVO register(RegisterDTO dto) {
        String account = dto.getAccount().trim();
        if (userBaseMapper.countByAccount(account) > 0) {
            throw new BizException("ACCOUNT_EXISTS", "Account already exists");
        }

        String passwordHash = passwordEncoder.encode(dto.getPassword());
        String displayName = StringUtils.hasText(dto.getName()) ? dto.getName().trim() : account;

        int baseInserted = userBaseMapper.insertUserBase(account, passwordHash, ACTIVE_STATUS);
        if (baseInserted != 1) {
            throw new BizException("REGISTER_FAILED", "Failed to create user base record");
        }

        int profileInserted = userProfileMapper.insertUserProfile(account, displayName, null, null);
        if (profileInserted != 1) {
            throw new BizException("REGISTER_FAILED", "Failed to create user profile record");
        }

        Long userId = userBaseMapper.selectIdByAccount(account);
        if (userId == null) {
            throw new BizException("REGISTER_FAILED", "Failed to fetch generated user id");
        }

        userSearchService.syncByAccount(account);
        return UserRegisterVO.builder()
                .userId(userId)
                .account(account)
                .build();
    }

    @Override
    public UserRegisterVO login(LoginDTO dto) {
        String account = dto.getAccount().trim();
        String passwordHash = userBaseMapper.selectPasswordHashByAccount(account);
        if (!StringUtils.hasText(passwordHash) || !passwordEncoder.matches(dto.getPassword(), passwordHash)) {
            throw new BizException("ACCOUNT_OR_PASSWORD_INVALID", "Account or password is incorrect");
        }

        Integer status = userBaseMapper.selectStatusByAccount(account);
        if (status == null || status != ACTIVE_STATUS) {
            throw new BizException("USER_DISABLED", "User is disabled");
        }

        Long userId = userBaseMapper.selectIdByAccount(account);
        if (userId == null) {
            throw new BizException("USER_NOT_FOUND", "User not found");
        }

        return UserRegisterVO.builder()
                .userId(userId)
                .account(account)
                .build();
    }

    @Override
    @Transactional
    public void updateSign(Long userId, String sign) {
        int affected = userProfileMapper.updateSignByUserId(userId, sign);
        if (affected != 1) {
            throw new BizException("USER_NOT_FOUND", "User not found");
        }
        String account = requireAccountByUserId(userId);
        userSearchService.syncByAccount(account);
    }

    @Override
    @Transactional
    public void updateAvatar(Long userId, String avatarUrl) {
        int affected = userProfileMapper.updateAvatarUrlByUserId(userId, avatarUrl);
        if (affected != 1) {
            throw new BizException("USER_NOT_FOUND", "User not found");
        }
        String account = requireAccountByUserId(userId);
        userSearchService.syncByAccount(account);
    }

    @Override
    @Transactional
    public void updateName(Long userId, String name) {
        int affected = userProfileMapper.updateNameByUserId(userId, name);
        if (affected != 1) {
            throw new BizException("USER_NOT_FOUND", "User not found");
        }
        String account = requireAccountByUserId(userId);
        userSearchService.syncByAccount(account);
    }

    @Override
    @Transactional
    public void updatePassword(Long userId, UpdatePasswordDTO dto) {
        String currentHash = userBaseMapper.selectPasswordHashByUserId(userId);
        if (!StringUtils.hasText(currentHash)) {
            throw new BizException("USER_NOT_FOUND", "User not found");
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), currentHash)) {
            throw new BizException("PASSWORD_INCORRECT", "Old password is incorrect");
        }
        if (dto.getOldPassword().equals(dto.getNewPassword())) {
            throw new BizException("INVALID_PARAM", "New password must be different from old password");
        }

        String newHash = passwordEncoder.encode(dto.getNewPassword());
        int affected = userBaseMapper.updatePasswordHashByUserId(userId, newHash);
        if (affected != 1) {
            throw new BizException("PASSWORD_UPDATE_FAILED", "Failed to update password");
        }
    }

    @Override
    public UserPublicProfileVO getPublicProfile(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException("INVALID_PARAM", "userId must be a positive number");
        }
        UserPublicProfileVO profile = userProfileMapper.selectPublicProfileByUserId(userId);
        if (profile == null) {
            throw new BizException("USER_NOT_FOUND", "User not found");
        }
        return profile;
    }

    private String requireAccountByUserId(Long userId) {
        String account = userBaseMapper.selectAccountById(userId);
        if (!StringUtils.hasText(account)) {
            throw new BizException("USER_NOT_FOUND", "User not found");
        }
        return account;
    }
}
