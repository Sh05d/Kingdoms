package com.kingdom.Controller;

import com.kingdom.API.ApiResponse;
import com.kingdom.Config.CustomUserDetails;
import com.kingdom.Enums.UserRole;
import com.kingdom.DTO.IN.PlayerIn;
import com.kingdom.DTO.IN.WakatimeConnectIn;
import com.kingdom.Service.APIService.N8nAutomationService;
import com.kingdom.Service.PlayerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/player")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;
    private final N8nAutomationService n8nAutomationService;


    // The authenticated caller's identity from the login. Players get their full profile (displayName, username,
    // email, phoneNumber, joinedAt); admins have no player record, so they get their basic identity instead.
    @GetMapping("/me")
    public ResponseEntity<?> getMe(@AuthenticationPrincipal CustomUserDetails me) {
        if (me.getRole() == UserRole.ADMIN) {
            return ResponseEntity.status(200).body(java.util.Map.of(
                    "id", me.getId(), "username", me.getUsername(), "role", me.getRole()));
        }
        return ResponseEntity.status(200).body(playerService.getMyProfile(me.getId()));
    }

    @GetMapping("/get")
    public ResponseEntity<?> getMyProfile(@AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.status(200).body(playerService.getMyProfile(me.getId()));
    }

    @PostMapping("/add/{userId}")
    public ResponseEntity<?> addPlayer(@PathVariable Integer userId, @RequestBody @Valid PlayerIn playerIn) {
        playerService.addPlayer(userId, playerIn);
        return ResponseEntity.status(200).body(new ApiResponse("Player profile created successfully"));
    }

    @PutMapping("/update")
    public ResponseEntity<?> updateMyProfile(@AuthenticationPrincipal CustomUserDetails me, @RequestBody @Valid PlayerIn playerIn) {
        playerService.updateMyProfile(me.getId(), playerIn);
        return ResponseEntity.status(200).body(new ApiResponse("Profile updated successfully"));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteMyProfile(@AuthenticationPrincipal CustomUserDetails me) {
        playerService.deleteMyProfile(me.getId());
        return ResponseEntity.status(200).body(new ApiResponse("Profile deleted successfully"));
    }

    //Shahad endpoint
    @PostMapping("/ai-report")
    public ResponseEntity<?> sendPlayerAiReportEmail(@AuthenticationPrincipal CustomUserDetails me) {
        playerService.playerAiReport(me.getId());
        return ResponseEntity.status(200).body(new ApiResponse("AI player report sent by email successfully"));
    }

    //Shahad endpoint
    @GetMapping("/summary")
    public ResponseEntity<?> playerSummary(@AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.ok(playerService.playerSummary(me.getId()));
    }

    //Shahad endpoint
    @GetMapping("/best-kingdom")
    public ResponseEntity<?> bestKingdom(@AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.ok(playerService.bestKingdom(me.getId()));
    }

    //Shahad endpoint
    @GetMapping("/kingdoms")
    public ResponseEntity<?> playerKingdoms(@AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.ok(playerService.playerKingdoms(me.getId()));
    }

    //Shahad endpoint
    @GetMapping("/highest-streak")
    public ResponseEntity<?> highestStreak(@AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.ok(new ApiResponse(playerService.highestStreak(me.getId())));
    }

    @GetMapping("/churn-risk")
    public ResponseEntity<?> getChurnRiskPlayers() {
        return ResponseEntity.ok(n8nAutomationService.getChurnRiskPlayers());
    }
    @GetMapping("/weekly-reports")
    public ResponseEntity<?> getWeeklyReports() {
        return ResponseEntity.ok(n8nAutomationService.getWeeklyReports());
    }
    @PostMapping("/connect-wakatime")
    public ResponseEntity<?> connectWakatime(@AuthenticationPrincipal CustomUserDetails me, @RequestBody @Valid WakatimeConnectIn wakatimeConnectIn) {
        playerService.connectWakatime(me.getId(), wakatimeConnectIn);
        return ResponseEntity.ok(new ApiResponse("WakaTime account connected successfully"));
    }
}
