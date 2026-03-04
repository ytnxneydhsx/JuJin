package org.example.backend.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateNameDTO {

    @NotBlank(message = "name is required")
    @Size(max = 64, message = "name length must be <= 64")
    private String name;
}
