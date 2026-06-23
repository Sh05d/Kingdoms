package com.kingdom.Controller;

import com.kingdom.Config.CustomUserDetails;
import com.kingdom.Enums.InviteStatus;
import com.kingdom.Service.LobbyInviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.kingdom.API.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/invite")
@RequiredArgsConstructor
public class LobbyInviteController {

    private final LobbyInviteService lobbyInviteService;

    @GetMapping("/get")
    public ResponseEntity<?> getAllInvites() {
        return ResponseEntity.ok(lobbyInviteService.getAllInvites());
    }

    @GetMapping("/get/{inviteId}")
    public ResponseEntity<?> getInviteById(@PathVariable Integer inviteId) {
        return ResponseEntity.ok(lobbyInviteService.getInviteById(inviteId));
    }

    @PostMapping("/send/{lobbyId}/{username}")
    public ResponseEntity<?> sendInvite(@PathVariable Integer lobbyId, @AuthenticationPrincipal CustomUserDetails me, @PathVariable String username) {
        lobbyInviteService.sendInvite(lobbyId, me.getId(), username);
        return ResponseEntity.ok(new ApiResponse("Invite sent successfully"));
    }

    @PutMapping("/update-status/{inviteId}/{status}")
    public ResponseEntity<?> updateInviteStatus(@PathVariable Integer inviteId, @PathVariable InviteStatus status) {
        lobbyInviteService.updateInviteStatus(inviteId, status);
        return ResponseEntity.ok(new ApiResponse("Invite status updated successfully"));
    }

    @DeleteMapping("/delete/{inviteId}")
    public ResponseEntity<?> deleteInvite(@PathVariable Integer inviteId) {
        lobbyInviteService.deleteInvite(inviteId);
        return ResponseEntity.ok(new ApiResponse("Invite deleted successfully"));
    }

    @PostMapping("/resend/{inviteId}")
    public ResponseEntity<?> resendInvite(@PathVariable Integer inviteId) {
        lobbyInviteService.resendInvite(inviteId);
        return ResponseEntity.ok(new ApiResponse("Invite resent successfully"));
    }

    @PutMapping("/reject/{inviteId}")
    public ResponseEntity<?> rejectInvite(@PathVariable Integer inviteId, @AuthenticationPrincipal CustomUserDetails me) {
        lobbyInviteService.rejectInvite(inviteId, me.getId());
        return ResponseEntity.status(200).body(new ApiResponse("Invite rejected successfully"));
    }

    @GetMapping("/my-pending")
    public ResponseEntity<?> getMyPendingInvites(@AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.status(200).body(lobbyInviteService.getMyPendingInvites(me.getId()));
    }
}
