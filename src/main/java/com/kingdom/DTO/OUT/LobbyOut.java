package com.kingdom.DTO.OUT;

import com.kingdom.Enums.LobbyStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class LobbyOut {
    private Integer id;
    private String name;
    private String challengeTitle;
    private String description;
    private LobbyStatus status;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private String kingdomName;
}
