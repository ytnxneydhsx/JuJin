package org.example.backend.service.search.user;

public interface UserSearchIndexService {

    long rebuildIndex();

    void syncByAccount(String account);
}
