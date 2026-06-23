package com.kingdom.DTO.OUT;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class FinishedLobby {
    private String name;
    private String description;
    private String challengeTitle;
    private String challengeDifficulty;
    private String WinnerName;
}
