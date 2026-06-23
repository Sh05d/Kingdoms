package com.kingdom.DTO.OUT;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlayerSummary {
    private Integer totalXp;
    private Integer numberOfCompletedChallenge;
    private Integer numberOfJoinedKingdom;
}