package com.kingdom.Controller;

import com.kingdom.Service.PlayerBadgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.kingdom.API.ApiResponse;
import com.kingdom.Config.CustomUserDetails;
import com.kingdom.Model.PlayerBadge;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/player-badge")
@RequiredArgsConstructor
public class PlayerBadgeController {
    private final PlayerBadgeService playerBadgeService;

    @GetMapping("/get")
    public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(playerBadgeService.getAllPlayerBadges());
    }

    @PostMapping("/add/{badgeId}/{membershipId}")
    public ResponseEntity<?> add(@PathVariable Integer badgeId, @PathVariable Integer membershipId) {
        playerBadgeService.addPlayerBadge(badgeId, membershipId);
        return ResponseEntity.ok(new ApiResponse("Player badge created successfully"));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody @Valid PlayerBadge playerBadge) {
        playerBadgeService.updatePlayerBadge(id, playerBadge);
        return ResponseEntity.ok(new ApiResponse("Player badge updated successfully"));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        playerBadgeService.deletePlayerBadge(id);
        return ResponseEntity.ok(new ApiResponse("Player badge deleted successfully"));
    }

    //Shahad endpoint
    @GetMapping("/player-badges")
    public ResponseEntity<?> playerBadges(@AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.ok(playerBadgeService.playerBadges(me.getId()));
    }

    //Shahad endpoint
    @GetMapping("/{kingdomId}/member-badges")
    public ResponseEntity<?> earnedKingdomBadges(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Integer kingdomId) {
        return ResponseEntity.ok(playerBadgeService.earnedKingdomBadges(me.getId(), kingdomId));
    }
}
