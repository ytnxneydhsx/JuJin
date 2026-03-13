package org.example.backend.common.auth;

import org.example.backend.config.LoginUserPrincipal;
import org.example.backend.exception.BizException;
import org.springframework.security.core.Authentication;

public final class AuthUtils {

    private static final String LOGIN_REQUIRED_CODE = "UNAUTHORIZED";
    private static final String LOGIN_REQUIRED_MESSAGE = "Please login first";

    private AuthUtils() {
    }

    public static LoginUserPrincipal requireLoginPrincipal(Authentication authentication) {
        LoginUserPrincipal principal = tryGetLoginPrincipal(authentication);
        if (principal == null || principal.getUserId() == null) {
            throw unauthorized();
        }
        return principal;
    }

    public static Long requireLoginUserId(Authentication authentication) {
        return requireLoginPrincipal(authentication).getUserId();
    }

    public static Long tryGetLoginUserId(Authentication authentication) {
        LoginUserPrincipal principal = tryGetLoginPrincipal(authentication);
        return principal == null ? null : principal.getUserId();
    }

    public static LoginUserPrincipal tryGetLoginPrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUserPrincipal principal)) {
            return null;
        }
        return principal;
    }

    private static BizException unauthorized() {
        return new BizException(LOGIN_REQUIRED_CODE, LOGIN_REQUIRED_MESSAGE);
    }
}
