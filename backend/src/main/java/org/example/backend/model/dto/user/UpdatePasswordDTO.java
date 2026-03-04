package org.example.backend.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdatePasswordDTO {

    @NotBlank(message = "oldPassword is required")
    @Size(min = 6, max = 64, message = "oldPassword length must be between 6 and 64")
    private String oldPassword;

    @NotBlank(message = "newPassword is required")
    @Size(min = 6, max = 64, message = "newPassword length must be between 6 and 64")
    private String newPassword;
}
