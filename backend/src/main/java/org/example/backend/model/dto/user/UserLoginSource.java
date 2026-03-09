package org.example.backend.model.dto.user;

import lombok.Data;

@Data
public class UserLoginSource {

    private Long userId;
    private String account;
    private String passwordHash;
    private Integer status;
}
