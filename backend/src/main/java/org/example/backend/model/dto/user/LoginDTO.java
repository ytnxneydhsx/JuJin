package org.example.backend.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginDTO {

    @NotBlank(message = "account is required")
    @Size(min = 3, max = 64, message = "account length must be between 3 and 64")
    private String account;

    @NotBlank(message = "password is required")
    @Size(min = 6, max = 64, message = "password length must be between 6 and 64")
    private String password;
}

