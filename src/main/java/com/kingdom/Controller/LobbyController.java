package com.kingdom.Controller;

import com.kingdom.API.ApiResponse;
import com.kingdom.Config.CustomUserDetails;
import com.kingdom.DTO.IN.LobbyIN;
import com.kingdom.Service.APIService.N8nAutomationService;
import com.kingdom.Service.LobbyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lobby")
@RequiredArgsConstructor
public class LobbyController {

    private final LobbyService lobbyService;
    private final N8nAutomationService n8nAutomationService;

    @GetMapping("/get")
    public ResponseEntity<?> getAllLobbies() {
        return ResponseEntity.status(200).body(lobbyService.getAllLobbies());
    }

    @PostMapping("/create/{kingdomId}/{challengeId}")
    public ResponseEntity<?> createLobby(@PathVariable Integer kingdomId, @PathVariable Integer challengeId, @AuthenticationPrincipal CustomUserDetails me, @RequestBody @Valid LobbyIN lobbyIn) {
        Integer lobbyId = lobbyService.createLobby(kingdomId, challengeId, me.getId(), lobbyIn);
        return ResponseEntity.status(200).body(java.util.Map.of("message", "تم إنشاء اللوبي بنجاح", "lobbyId", lobbyId));
    }
    @PutMapping("/update/{lobbyId}")
    public ResponseEntity<?> updateLobby(@PathVariable Integer lobbyId, @RequestBody @Valid LobbyIN lobbyIn) {
        lobbyService.updateLobby(lobbyId, lobbyIn);
        return ResponseEntity.status(200).body(new ApiResponse("Lobby updated successfully"));
    }

    @DeleteMapping("/delete/{lobbyId}")
    public ResponseEntity<?> deleteLobby(@PathVariable Integer lobbyId) {
        lobbyService.deleteLobby(lobbyId);
        return ResponseEntity.status(200).body(new ApiResponse("Lobby deleted successfully"));
    }

    @GetMapping("/get/{lobbyId}")
    public ResponseEntity<?> getLobbyById(@PathVariable Integer lobbyId) {
        return ResponseEntity.status(200).body(lobbyService.getLobbyById(lobbyId));
    }

    @GetMapping("/public/{kingdomId}")
    public ResponseEntity<?> getPublicLobbiesByKingdom(@PathVariable Integer kingdomId, @AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.status(200).body(lobbyService.getPublicLobbiesByKingdom(kingdomId, me.getId()));
    }

    @DeleteMapping("/cancel/{lobbyId}")
    public ResponseEntity<?> cancelLobby(@PathVariable Integer lobbyId, @AuthenticationPrincipal CustomUserDetails me) {
        lobbyService.cancelLobby(lobbyId, me.getId());
        return ResponseEntity.status(200).body(new ApiResponse("Lobby cancelled successfully"));
    }
    @GetMapping("/my")
    public ResponseEntity<?> getMyLobbies(@AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.status(200).body(lobbyService.getMyLobbies(me.getId()));
    }
    @GetMapping("/available")
    public ResponseEntity<?> getAvailableLobbies(@AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.ok(lobbyService.getAvailableLobbies(me.getId()));
    }

    @GetMapping("/my-private/{kingdomId}")
    public ResponseEntity<?> getMyPrivateLobbies(@PathVariable Integer kingdomId, @AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.status(200).body(lobbyService.getMyPrivateLobbies(kingdomId, me.getId()));
    }

    @PostMapping("/finish/{lobbyId}/{winnerPlayerId}")
    public ResponseEntity<?> finishLobby(@PathVariable Integer lobbyId, @AuthenticationPrincipal CustomUserDetails me, @PathVariable Integer winnerPlayerId) {
        lobbyService.finishLobby(lobbyId, me.getId(), winnerPlayerId);
        return ResponseEntity.status(200).body(new ApiResponse("Lobby finished successfully"));
    }

    //Shahad endpoint
    @GetMapping("/{lobbyId}/member-count")
    public ResponseEntity<?> memberCount(@PathVariable Integer lobbyId) {
        return ResponseEntity.ok(new ApiResponse(lobbyService.lobbyMemberCount(lobbyId)));
    }

    //Shahad endpoint
    @GetMapping("/my-finished")
    public ResponseEntity<?> playerFinishedLobbies(@AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.ok(lobbyService.playerFinishedLobbies(me.getId()));
    }

    @GetMapping("/suggest")
    public ResponseEntity<?> suggestLobbyForPlayer(@AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.status(200).body(lobbyService.suggestLobbyForPlayer(me.getId()));
    }
    @GetMapping("/host/{lobbyId}")
    public ResponseEntity<?> getLobbyHost(@PathVariable Integer lobbyId) {
        return ResponseEntity.ok(lobbyService.getLobbyHost(lobbyId));
    }

    @GetMapping("/winners")
    public ResponseEntity<?> getLobbyWinners() {
        return ResponseEntity.ok(n8nAutomationService.getLobbyWinners());
    }

    @GetMapping("/active")
    public ResponseEntity<?> getMyActiveLobbies(@AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.ok(lobbyService.getMyActiveLobbies(me.getId()));
    }
}
