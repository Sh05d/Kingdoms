package com.kingdom.Controller;

import com.kingdom.API.ApiResponse;
import com.kingdom.DTO.IN.UserIn;
import com.kingdom.Service.AdminService;
import com.kingdom.Service.KingdomMembershipService;
import com.kingdom.Service.LobbyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final KingdomMembershipService kingdomMembershipService;
    private final LobbyService lobbyService;

    @GetMapping
    public ResponseEntity<?> getAllUsers() {
        return ResponseEntity.status(200).body(adminService.getAllUsers());
    }

    @PostMapping("/add")
    public ResponseEntity<?> addUser(@RequestBody @Valid UserIn userIn) {
        adminService.addUser(userIn);
        return ResponseEntity.status(200).body(new ApiResponse("User created successfully"));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Integer id, @RequestBody @Valid UserIn userIn) {
        adminService.updateUser(id, userIn);
        return ResponseEntity.status(200).body(new ApiResponse("User updated successfully"));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Integer id) {
        adminService.deleteUser(id);
        return ResponseEntity.status(200).body(new ApiResponse("User deleted successfully"));
    }
    @GetMapping("byID/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Integer id) {
        return ResponseEntity.status(200).body(adminService.getUserById(id));
    }

    @GetMapping("/statistics")
    public ResponseEntity<?> getStatistics() {
        return ResponseEntity.status(200).body(adminService.adminGetStatistics());
    }

    @GetMapping("/players")
    public ResponseEntity<?> getAllPlayers() {
        return ResponseEntity.status(200).body(adminService.adminGetAllPlayers());
    }

    @GetMapping("/challenges")
    public ResponseEntity<?> getAllChallenges() {
        return ResponseEntity.status(200).body(adminService.adminGetAllChallenges());
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<?> getAllSubscriptions() {
        return ResponseEntity.status(200).body(adminService.adminGetAllSubscriptions());
    }

    @GetMapping("/lobbies")
    public ResponseEntity<?> getAllLobbies() {
        return ResponseEntity.status(200).body(adminService.adminGetAllLobbies());
    }

    @GetMapping("/challenge-progress")
    public ResponseEntity<?> getAllChallengeProgress() {
        return ResponseEntity.status(200).body(adminService.adminGetAllChallengeProgress());
    }

    @GetMapping("/challenge-progress/pending")
    public ResponseEntity<?> getPendingChallengeProgress() {
        return ResponseEntity.status(200).body(adminService.adminGetPendingChallengeProgress());
    }

    @GetMapping("/kingdoms")
    public ResponseEntity<?> getAllKingdoms() {
        return ResponseEntity.status(200).body(adminService.adminGetAllKingdoms());
    }

    @GetMapping("/kingdom/{kingdomId}")
    public ResponseEntity<?> getKingdomById(@PathVariable Integer kingdomId) {
        return ResponseEntity.status(200).body(adminService.adminGetKingdomById(kingdomId));
    }
    @PutMapping("/player/{playerId}/ban")
    public ResponseEntity<?> banPlayer(@PathVariable Integer playerId) {
        adminService.banPlayer(playerId);
        return ResponseEntity.ok(new ApiResponse("Player banned successfully"));
    }

    @PutMapping("/player/{playerId}/unban")
    public ResponseEntity<?> unbanPlayer(@PathVariable Integer playerId) {
        adminService.unbanPlayer(playerId);
        return ResponseEntity.ok(new ApiResponse("Player unbanned successfully"));
    }

    @PutMapping("/membership/{membershipId}/adjust-xp/{xp}")
    public ResponseEntity<?> adjustMembershipXp(@PathVariable Integer membershipId, @PathVariable Integer xp) {
        kingdomMembershipService.adjustXpByAdmin(membershipId, xp);
        return ResponseEntity.ok(new ApiResponse("XP adjusted successfully"));
    }
    @DeleteMapping("/lobby/{lobbyId}/force-delete")
    public ResponseEntity<?> forceDeleteLobby(@PathVariable Integer lobbyId) {
        lobbyService.forceDeleteLobby(lobbyId);
        return ResponseEntity.ok(new ApiResponse("Lobby force deleted successfully"));
    }
}