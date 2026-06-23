package com.kingdom.DTO.IN;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserIn {

    @NotEmpty(message = "email cannot be empty")
    @Email(message = "invalid email format")
    private String email;

    @NotEmpty(message = "username cannot be empty")
    @Size(min = 4, max = 20, message = "username must be between 4 and 20 characters")
    private String username;

    @NotEmpty(message = "Phone Number is required")
    @Size(max = 15, message = "Phone Number must not exceed 15 characters")
    private String phoneNumber;

    @NotEmpty(message = "Password is required")
    @Size(min = 8, max = 50, message = "Password must be between 8 and 50 characters")
    private String password;
}
