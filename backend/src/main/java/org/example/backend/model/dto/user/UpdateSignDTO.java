package org.example.backend.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateSignDTO {

    @NotBlank(message = "sign is required")
    @Size(max = 255, message = "sign length must be <= 255")
    private String sign;
}
