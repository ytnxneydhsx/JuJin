package org.example.backend.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateAvatarDTO {

    @NotBlank(message = "avatarUrl is required")
    @Size(max = 512, message = "avatarUrl length must be <= 512")
    private String avatarUrl;
}
