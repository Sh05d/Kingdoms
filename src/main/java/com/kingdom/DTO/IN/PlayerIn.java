package com.kingdom.DTO.IN;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlayerIn {
    @NotEmpty(message = "email cannot be empty")
    @Email(message = "invalid email format")
    private String email;

    @NotEmpty(message = "username cannot be empty")
    @Size(min = 4, max = 20, message = "username must be between 4 and 20 characters")
    private String username;

    @NotEmpty(message = "Phone Number is required")
    @Pattern(regexp = "^9665\\d{8}$", message = "Number should be valid Saudi number start with +966")
    private String phoneNumber;

    @NotEmpty(message = "Password is required")
    @Size(min = 8, max = 50, message = "Password must be between 8 and 50 characters")
    private String password;

    @NotEmpty(message = "name cannot be empty")
    private String displayName;
}
