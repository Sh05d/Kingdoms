package com.kingdom.DTO.OUT;

import com.kingdom.Enums.ProgressStatus;
import com.kingdom.Model.ChallengeProgress;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Clean response shape for a ChallengeProgress (a player's run): the challenge flattened to id/title, plus the
 * status and timestamps. The owning player/kingdom ids and internal verification state (rejectionReason /
 * verifiedValue) are left out — these are the caller's own runs, so the player id is redundant.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeProgressOut {

    private Integer id;
    private ProgressStatus status;
    private LocalDateTime startAt;
    private LocalDateTime finishedAt;

    // Flattened from the challenge relation:
    private Integer challengeId;
    private String challengeTitle;

    /** Map one ChallengeProgress entity to its clean output shape. */
    public static ChallengeProgressOut from(ChallengeProgress p) {
        ChallengeProgressOut out = new ChallengeProgressOut();
        out.id = p.getId();
        out.status = p.getStatus();
        out.startAt = p.getStartAt();
        out.finishedAt = p.getFinishedAt();

        if (p.getChallenge() != null) {
            out.challengeId = p.getChallenge().getId();
            out.challengeTitle = p.getChallenge().getTitle();
        }
        return out;
    }

    /** Map a list of ChallengeProgress entities to clean output shapes. */
    public static List<ChallengeProgressOut> fromList(List<ChallengeProgress> progresses) {
        List<ChallengeProgressOut> list = new ArrayList<>();
        for (ChallengeProgress p : progresses) {
            list.add(from(p));
        }
        return list;
    }
}
