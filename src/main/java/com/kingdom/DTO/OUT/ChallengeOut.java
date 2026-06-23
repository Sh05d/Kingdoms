package com.kingdom.DTO.OUT;

import com.kingdom.Enums.Difficulty;
import com.kingdom.Enums.Period;
import com.kingdom.Model.Challenge;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Clean response shape for a Challenge — only what a player needs to see. Backend verification details
 * (verificationSource / metricKey / targetValue) and the create/end timestamps are intentionally left out: the description
 * states the goal in plain language and the period conveys the timeframe.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeOut {

    private Integer id;
    private Integer kingdomId;
    private String title;
    private String description;
    private Period period;
    private Difficulty difficulty;
    private Integer xpReward;

    /** Map one Challenge entity to its clean output shape. */
    public static ChallengeOut from(Challenge c) {
        ChallengeOut out = new ChallengeOut();
        out.id = c.getId();
        out.kingdomId = c.getKingdom() != null ? c.getKingdom().getId() : null;
        out.title = c.getTitle();
        out.description = c.getDescription();
        out.period = c.getPeriod();
        out.difficulty = c.getDifficulty();
        out.xpReward = c.getXpReward();
        return out;
    }

    /** Map a list of Challenge entities to clean output shapes. */
    public static List<ChallengeOut> fromList(List<Challenge> challenges) {
        List<ChallengeOut> list = new ArrayList<>();
        for (Challenge c : challenges) {
            list.add(from(c));
        }
        return list;
    }
}
