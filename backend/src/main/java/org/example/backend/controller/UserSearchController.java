package org.example.backend.controller;

import org.example.backend.common.response.PageResult;
import org.example.backend.common.response.Result;
import lombok.RequiredArgsConstructor;
import org.example.backend.model.vo.UserSearchVO;
import org.example.backend.service.search.UserSearchService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search/users")
@RequiredArgsConstructor
public class UserSearchController {

    private final UserSearchService userSearchService;

    @GetMapping
    public Result<PageResult<UserSearchVO>> search(@RequestParam(value = "q", required = false) String query,
                                                   @RequestParam(value = "userId", required = false) Long userId,
                                                   @RequestParam(value = "page", defaultValue = "0") int page,
                                                   @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<UserSearchVO> pageData = userSearchService.search(query, userId, page, size);
        return Result.success(PageResult.from(pageData));
    }
}
