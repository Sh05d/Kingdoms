package com.kingdom.DTO.IN;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class LobbyInviteIN {
    @NotEmpty(message = "username cannot be empty")
    private String invitedUsername;
}
