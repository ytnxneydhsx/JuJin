package org.example.backend.service.core.draft;

import org.example.backend.model.dto.article.SaveDraftDTO;
import org.example.backend.model.vo.ArticleDraftVO;
import org.springframework.data.domain.Page;

public interface DraftService {

    Long createDraft(Long userId, SaveDraftDTO dto);

    void updateDraft(Long userId, Long draftId, SaveDraftDTO dto);

    ArticleDraftVO getDraft(Long userId, Long draftId);

    Page<ArticleDraftVO> listDrafts(Long userId, int page, int size);

    Long publishDraft(Long userId, Long draftId);

    void deleteDraft(Long userId, Long draftId);
}
