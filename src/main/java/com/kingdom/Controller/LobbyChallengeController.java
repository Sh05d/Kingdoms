package com.kingdom.Controller;

import com.kingdom.API.ApiResponse;
import com.kingdom.Service.LobbyChallengeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

// =================================================================================================
// OPTIONAL FEATURE (Anas) — see LobbyChallengeService.
// To DISABLE the lobby-challenge feature, COMMENT OUT (slash out) this whole class + LobbyChallengeService.
// =================================================================================================
@RestController
@RequestMapping("/api/v1/lobby-challenge")
@RequiredArgsConstructor
public class LobbyChallengeController {

    private final LobbyChallengeService lobbyChallengeService;

    // Resolve a lobby's challenge: first member to finish + verify wins, then the lobby is removed.
    @PostMapping("/resolve/{lobbyId}")
    public ApiResponse resolveWinner(@PathVariable Integer lobbyId) {
        Integer winnerPlayerId = lobbyChallengeService.resolveWinner(lobbyId);
        if (winnerPlayerId == null) {
            return new ApiResponse("No lobby member has finished the challenge yet — no winner");
        }
        return new ApiResponse("Winner is player " + winnerPlayerId + "; lobby removed");
    }
}
