package org.example.backend.service.search.user.impl;

import lombok.RequiredArgsConstructor;
import org.example.backend.common.constant.AppConstants.UserStatus;
import org.example.backend.mapper.search.UserSearchMapper;
import org.example.backend.model.dto.UserSearchSource;
import org.example.backend.model.es.UserSearchDocument;
import org.example.backend.repository.UserSearchRepository;
import org.example.backend.service.search.user.UserSearchIndexService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserSearchIndexServiceImpl implements UserSearchIndexService {

    private final UserSearchMapper userSearchMapper;
    private final UserSearchRepository userSearchRepository;

    @Override
    public long rebuildIndex() {
        List<UserSearchSource> sources = userSearchMapper.selectAllForSearch();
        List<UserSearchDocument> docs = sources.stream()
                .filter(source -> source.getStatus() != null && source.getStatus() == UserStatus.ACTIVE)
                .map(this::toDocument)
                .toList();

        userSearchRepository.deleteAll();
        userSearchRepository.saveAll(docs);
        return docs.size();
    }

    @Override
    public void syncByAccount(String account) {
        UserSearchSource source = userSearchMapper.selectByAccountForSearch(account);
        if (source == null || source.getStatus() == null || source.getStatus() != UserStatus.ACTIVE) {
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
                .build();
    }
}
