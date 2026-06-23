package com.kingdom.DTO.OUT;

import com.kingdom.Enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserOut {

    private Integer id;

    private String email;

    private String username;

    private String phoneNumber;

    private UserRole role;

    private LocalDateTime createdAt;
}