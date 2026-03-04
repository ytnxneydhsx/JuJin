package org.example.backend.repository;

import org.example.backend.model.es.UserSearchDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface UserSearchRepository extends ElasticsearchRepository<UserSearchDocument, String> {

    Page<UserSearchDocument> findByNameContaining(String nameKeyword, Pageable pageable);

    Page<UserSearchDocument> findByUserId(Long userId, Pageable pageable);

    Page<UserSearchDocument> findByUserIdAndNameContaining(Long userId,
                                                           String nameKeyword,
                                                           Pageable pageable);

}
