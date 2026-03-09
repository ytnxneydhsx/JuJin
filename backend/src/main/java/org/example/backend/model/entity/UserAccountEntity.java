package org.example.backend.model.entity;

import lombok.Data;

@Data
public class UserAccountEntity {

    private Long id;
    private String account;
    private String passwordHash;
    private Integer status;
}
