package org.example.backend.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPublicProfileVO {

    private Long userId;
    private String name;
    private String avatarUrl;
    private String sign;
}

