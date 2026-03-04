package org.example.backend.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserSearchSource {
    private Long id;
    private String account;
    private String name;
    private Integer status;
    private String avatarUrl;
    private String sign;
    private LocalDateTime updatedAt;
}
