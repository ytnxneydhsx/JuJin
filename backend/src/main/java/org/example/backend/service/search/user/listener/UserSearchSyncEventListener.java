package org.example.backend.service.search.user.listener;

import org.example.backend.event.user.UserSearchSyncEvent;
import org.example.backend.service.search.user.UserSearchIndexService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserSearchSyncEventListener {

    private final UserSearchIndexService userSearchIndexService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserSearchSync(UserSearchSyncEvent event) {
        userSearchIndexService.syncByAccount(event.account());
    }
}
