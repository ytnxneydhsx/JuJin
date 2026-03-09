package org.example.backend.service.search.user.listener;

import org.example.backend.event.user.UserSearchSyncEvent;
import org.example.backend.service.search.user.UserSearchService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserSearchSyncEventListener {

    private final UserSearchService userSearchService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserSearchSync(UserSearchSyncEvent event) {
        userSearchService.syncByAccount(event.account());
    }
}
