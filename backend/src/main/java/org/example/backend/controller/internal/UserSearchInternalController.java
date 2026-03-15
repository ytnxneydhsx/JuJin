package org.example.backend.controller.internal;

import lombok.RequiredArgsConstructor;
import org.example.backend.common.response.Result;
import org.example.backend.model.vo.UserSearchRebuildVO;
import org.example.backend.model.vo.UserSearchSyncVO;
import org.example.backend.service.search.user.UserSearchIndexService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/search/users")
@RequiredArgsConstructor
public class UserSearchInternalController {

    private final UserSearchIndexService userSearchIndexService;

    @PostMapping("/rebuild-index")
    public Result<UserSearchRebuildVO> rebuildIndex() {
        long indexedCount = userSearchIndexService.rebuildIndex();
        return Result.success("User index rebuilt successfully",
                UserSearchRebuildVO.builder().indexedCount(indexedCount).build());
    }

    @PostMapping("/sync/{account}")
    public Result<UserSearchSyncVO> syncByAccount(@PathVariable("account") String account) {
        userSearchIndexService.syncByAccount(account);
        return Result.success("User index synced successfully",
                UserSearchSyncVO.builder().account(account).build());
    }
}
