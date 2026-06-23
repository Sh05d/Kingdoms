package com.kingdom.DTO.OUT;

import com.kingdom.Enums.MemberRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class LobbyMemberOut {
    private Integer id;
    private String displayName;
    private String username;
    private MemberRole role;
    private LocalDateTime joinedAt;
}
