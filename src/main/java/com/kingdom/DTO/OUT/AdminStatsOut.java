package com.kingdom.DTO.OUT;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsOut {

    private long totalPlayers;
    private long totalUsers;
    private long totalKingdoms;
    private long totalChallenges;
    private long totalLobbies;
    private long activeSubscriptions;
    private long pendingChallengeProgress;
}
