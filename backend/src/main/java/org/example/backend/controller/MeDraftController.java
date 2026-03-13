package org.example.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.backend.common.auth.AuthUtils;
import org.example.backend.common.response.PageResult;
import org.example.backend.common.response.Result;
import org.example.backend.model.dto.article.SaveDraftDTO;
import org.example.backend.model.vo.ArticleDraftVO;
import org.example.backend.model.vo.ArticleIdVO;
import org.example.backend.model.vo.DraftIdVO;
import org.example.backend.service.core.draft.DraftService;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/draft")
@RequiredArgsConstructor
public class MeDraftController {

    private final DraftService draftService;

    @PostMapping
    public Result<DraftIdVO> createDraft(@Valid @RequestBody SaveDraftDTO dto, Authentication authentication) {
        Long userId = AuthUtils.requireLoginUserId(authentication);
        Long draftId = draftService.createDraft(userId, dto);
        return Result.success("Draft created successfully", DraftIdVO.builder().draftId(draftId).build());
    }

    @PutMapping("/{draftId}")
    public Result<Void> updateDraft(@PathVariable("draftId") Long draftId,
                                    @Valid @RequestBody SaveDraftDTO dto,
                                    Authentication authentication) {
        Long userId = AuthUtils.requireLoginUserId(authentication);
        draftService.updateDraft(userId, draftId, dto);
        return Result.success("Draft updated successfully", null);
    }

    @GetMapping("/{draftId}")
    public Result<ArticleDraftVO> getDraft(@PathVariable("draftId") Long draftId,
                                           Authentication authentication) {
        Long userId = AuthUtils.requireLoginUserId(authentication);
        ArticleDraftVO draft = draftService.getDraft(userId, draftId);
        return Result.success(draft);
    }

    @GetMapping
    public Result<PageResult<ArticleDraftVO>> listDrafts(@RequestParam(value = "page", defaultValue = "0") int page,
                                                         @RequestParam(value = "size", defaultValue = "20") int size,
                                                         Authentication authentication) {
        Long userId = AuthUtils.requireLoginUserId(authentication);
        Page<ArticleDraftVO> pageData = draftService.listDrafts(userId, page, size);
        return Result.success(PageResult.from(pageData));
    }

    @PostMapping("/{draftId}/publish")
    public Result<ArticleIdVO> publishDraft(@PathVariable("draftId") Long draftId,
                                            Authentication authentication) {
        Long userId = AuthUtils.requireLoginUserId(authentication);
        Long articleId = draftService.publishDraft(userId, draftId);
        return Result.success("Article published successfully", ArticleIdVO.builder().articleId(articleId).build());
    }

    @DeleteMapping("/{draftId}")
    public Result<Void> deleteDraft(@PathVariable("draftId") Long draftId,
                                    Authentication authentication) {
        Long userId = AuthUtils.requireLoginUserId(authentication);
        draftService.deleteDraft(userId, draftId);
        return Result.success("Draft deleted successfully", null);
    }
}
