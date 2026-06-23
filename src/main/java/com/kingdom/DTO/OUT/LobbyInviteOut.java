package com.kingdom.DTO.OUT;

import com.kingdom.Enums.InviteStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class LobbyInviteOut {
    private Integer id;
    private String lobbyName;
    private Integer lobbyId;
    private InviteStatus status;
    private LocalDateTime sentAt;
    private LocalDateTime respondedAt;
    private Integer invitedPlayerId;


}
