package com.kingdom.DTO.IN;

import com.kingdom.Enums.ProgressStatus;
import com.kingdom.Enums.RejectionReason;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Request body for the generic create / update of a ChallengeProgress.
 *
 * NOTE: the normal way to start a run is the "join" endpoint (POST /join/{playerId}/{challengeId}),
 * which fills in the challenge + membership for you. This DTO is only for the generic add/update
 * (admin / testing), so the two links below are optional.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeProgressIn {

    @NotNull(message = "status is required")
    private ProgressStatus status;

    private LocalDateTime startAt;
    private LocalDateTime finishedAt;
    private RejectionReason rejectionReason;
    private Integer verifiedValue;

    // Optional links (resolved by id) for the generic "add". The "join" flow sets these for you.
    private Integer challengeId;
    private Integer kingdomMembershipId;
}
