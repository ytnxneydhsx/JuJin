package org.example.backend.service.search;

import org.example.backend.model.vo.UserSearchVO;
import org.springframework.data.domain.Page;

public interface UserSearchService {

    Page<UserSearchVO> search(String keyword, Long userId, int page, int size);

    long rebuildIndex();

    void syncByAccount(String account);
}
