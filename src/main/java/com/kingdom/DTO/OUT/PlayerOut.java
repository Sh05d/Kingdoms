package com.kingdom.DTO.OUT;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlayerOut {
    private Integer id;
    private String displayName;
    private String username;
    private String email;
    private String phoneNumber;
    private LocalDateTime joinedAt;
}