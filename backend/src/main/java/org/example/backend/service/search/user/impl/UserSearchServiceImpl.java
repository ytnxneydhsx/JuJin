package org.example.backend.service.search.user.impl;

import lombok.RequiredArgsConstructor;
import org.example.backend.exception.BizException;
import org.example.backend.mapper.search.UserSearchMapper;
import org.example.backend.model.dto.UserSearchSource;
import org.example.backend.model.es.UserSearchDocument;
import org.example.backend.model.vo.UserSearchVO;
import org.example.backend.repository.UserSearchRepository;
import org.example.backend.service.search.user.UserSearchService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserSearchServiceImpl implements UserSearchService {

    private static final int ACTIVE_STATUS = 1;

    private final UserSearchMapper userSearchMapper;
    private final UserSearchRepository userSearchRepository;

    @Override
    public Page<UserSearchVO> search(String keyword, Long userId, int page, int size) {
        if (!StringUtils.hasText(keyword) && userId == null) {
            throw new BizException("INVALID_PARAM", "Missing required query parameter: provide q or userId");
        }
        if (page < 0 || size <= 0) {
            throw new BizException("INVALID_PARAM", "Invalid pagination parameters: page must be >= 0 and size must be > 0");
        }

        Pageable pageable = PageRequest.of(page, size);
        if (!StringUtils.hasText(keyword)) {
            return userSearchRepository.findByUserId(userId, pageable).map(this::toSearchVO);
        }
        if (userId == null) {
            return userSearchRepository.findByNameContaining(keyword, pageable)
                    .map(this::toSearchVO);
        }
        return userSearchRepository.findByUserIdAndNameContaining(userId, keyword, pageable)
                .map(this::toSearchVO);
    }

    @Override
    public long rebuildIndex() {
        List<UserSearchSource> sources = userSearchMapper.selectAllForSearch();
        List<UserSearchDocument> docs = sources.stream()
                .filter(source -> source.getStatus() != null && source.getStatus() == ACTIVE_STATUS)
                .map(this::toDocument)
                .toList();

        userSearchRepository.deleteAll();
        userSearchRepository.saveAll(docs);
        return docs.size();
    }

    @Override
    public void syncByAccount(String account) {
        UserSearchSource source = userSearchMapper.selectByAccountForSearch(account);
        if (source == null || source.getStatus() == null || source.getStatus() != ACTIVE_STATUS) {
            userSearchRepository.deleteById(account);
            return;
        }
        userSearchRepository.save(toDocument(source));
    }

    private UserSearchDocument toDocument(UserSearchSource source) {
        return UserSearchDocument.builder()
                .id(source.getAccount())
                .userId(source.getId())
                .name(source.getName())
                .account(source.getAccount())
                .sign(source.getSign())
                .avatarUrl(source.getAvatarUrl())
                .status(source.getStatus())
                .updatedAt(source.getUpdatedAt())
                .build();
    }

    private UserSearchVO toSearchVO(UserSearchDocument document) {
        return UserSearchVO.builder()
                .id(document.getUserId())
                .name(document.getName())
                .build();
    }
}
