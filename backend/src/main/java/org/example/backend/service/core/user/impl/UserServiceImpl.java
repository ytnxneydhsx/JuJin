package org.example.backend.service.core.user.impl;

import lombok.RequiredArgsConstructor;
import org.example.backend.common.constant.AppConstants.UserStatus;
import org.example.backend.event.user.UserSearchSyncEvent;
import org.example.backend.exception.BizException;
import org.example.backend.mapper.user.UserAccountMapper;
import org.example.backend.mapper.user.UserProfileMapper;
import org.example.backend.model.dto.user.LoginDTO;
import org.example.backend.model.dto.user.RegisterDTO;
import org.example.backend.model.dto.user.UserLoginSource;
import org.example.backend.model.dto.user.UpdatePasswordDTO;
import org.example.backend.model.entity.UserAccountEntity;
import org.example.backend.model.vo.UserPublicProfileVO;
import org.example.backend.model.vo.UserRegisterVO;
import org.example.backend.service.core.user.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserAccountMapper userAccountMapper;
    private final UserProfileMapper userProfileMapper;
    private final ApplicationEventPublisher eventPublisher;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public UserRegisterVO register(RegisterDTO dto) {
        String account = dto.getAccount().trim();

        String passwordHash = passwordEncoder.encode(dto.getPassword());
        String displayName = StringUtils.hasText(dto.getName()) ? dto.getName().trim() : account;

        UserAccountEntity userAccount = new UserAccountEntity();
        userAccount.setAccount(account);
        userAccount.setPasswordHash(passwordHash);
        userAccount.setStatus(UserStatus.ACTIVE);

        int baseInserted;
        try {
            baseInserted = userAccountMapper.insert(userAccount);
        } catch (DuplicateKeyException ex) {
            throw new BizException("ACCOUNT_EXISTS", "Account already exists");
        }
        if (baseInserted != 1) {
            throw new BizException("REGISTER_FAILED", "Failed to create user base record");
        }

        int profileInserted = userProfileMapper.insertUserProfile(account, displayName, null, null);
        if (profileInserted != 1) {
            throw new BizException("REGISTER_FAILED", "Failed to create user profile record");
        }

        Long userId = userAccount.getId();
        if (userId == null) {
            throw new BizException("REGISTER_FAILED", "Failed to fetch generated user id");
        }

        publishUserSearchSyncEvent(account);
        return UserRegisterVO.builder()
                .userId(userId)
                .account(account)
                .build();
    }

    @Override
    public UserRegisterVO login(LoginDTO dto) {
        String account = dto.getAccount().trim();
        UserLoginSource loginSource = userAccountMapper.selectLoginSourceByAccount(account);
        if (loginSource == null
                || !StringUtils.hasText(loginSource.getPasswordHash())
                || !passwordEncoder.matches(dto.getPassword(), loginSource.getPasswordHash())) {
            throw new BizException("ACCOUNT_OR_PASSWORD_INVALID", "Account or password is incorrect");
        }

        Integer status = loginSource.getStatus();
        if (status == null || status != UserStatus.ACTIVE) {
            throw new BizException("USER_DISABLED", "User is disabled");
        }

        Long userId = loginSource.getUserId();
        if (userId == null) {
            throw new BizException("USER_NOT_FOUND", "User not found");
        }

        return UserRegisterVO.builder()
                .userId(userId)
                .account(loginSource.getAccount())
                .build();
    }

    @Override
    @Transactional
    public void updateSign(Long userId, String account, String sign) {
        int affected = userProfileMapper.updateSignByUserId(userId, sign);
        if (affected != 1) {
            throw new BizException("USER_NOT_FOUND", "User not found");
        }
        publishUserSearchSyncEvent(resolveAccount(userId, account));
    }

    @Override
    @Transactional
    public void updateAvatar(Long userId, String account, String avatarUrl) {
        int affected = userProfileMapper.updateAvatarUrlByUserId(userId, avatarUrl);
        if (affected != 1) {
            throw new BizException("USER_NOT_FOUND", "User not found");
        }
        publishUserSearchSyncEvent(resolveAccount(userId, account));
    }

    @Override
    @Transactional
    public void updateName(Long userId, String account, String name) {
        int affected = userProfileMapper.updateNameByUserId(userId, name);
        if (affected != 1) {
            throw new BizException("USER_NOT_FOUND", "User not found");
        }
        publishUserSearchSyncEvent(resolveAccount(userId, account));
    }

    @Override
    @Transactional
    public void updatePassword(Long userId, UpdatePasswordDTO dto) {
        String currentHash = userAccountMapper.selectPasswordHashByUserId(userId);
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
        int affected = userAccountMapper.updatePasswordHashByUserId(userId, newHash);
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

    private String resolveAccount(Long userId, String account) {
        if (!StringUtils.hasText(account)) {
            String accountFromDb = userAccountMapper.selectAccountByUserId(userId);
            if (!StringUtils.hasText(accountFromDb)) {
                throw new BizException("USER_NOT_FOUND", "User not found");
            }
            return accountFromDb.trim();
        }
        return account.trim();
    }

    private void publishUserSearchSyncEvent(String account) {
        eventPublisher.publishEvent(new UserSearchSyncEvent(account));
    }
}
