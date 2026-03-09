package org.example.backend.event.user;

import org.springframework.util.StringUtils;

public record UserSearchSyncEvent(String account) {

    public UserSearchSyncEvent {
        if (!StringUtils.hasText(account)) {
            throw new IllegalArgumentException("account cannot be blank");
        }
        account = account.trim();
    }
}
