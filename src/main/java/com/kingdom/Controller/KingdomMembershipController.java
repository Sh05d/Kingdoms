package com.kingdom.Controller;

import com.kingdom.API.ApiResponse;
import com.kingdom.Config.CustomUserDetails;
import com.kingdom.Model.Kingdom;
import com.kingdom.Model.KingdomMembership;
import com.kingdom.Service.KingdomMembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/kingdom-membership")
@RequiredArgsConstructor
public class KingdomMembershipController {

    private final KingdomMembershipService kingdomMembershipService;

    @GetMapping("/get")
    public ResponseEntity<?> getAllKingdomMemberships() {
        return ResponseEntity.ok(kingdomMembershipService.getAllKingdomMemberships());
    }

    @PostMapping("/join/{kingdomId}")
    public ResponseEntity<?> addKingdomMembership(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Integer kingdomId) {
        Kingdom kingdom = kingdomMembershipService.joinToKingdom(me.getId(), kingdomId);
        return ResponseEntity.ok(new ApiResponse("🎉 انضممت إلى مملكة " + kingdom.getType().getArabicName()
                + "! استمتع بالتحديات واكسب نقاط الخبرة."));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateKingdomMembership(@PathVariable Integer id, @RequestBody KingdomMembership kingdomMembership) {
        kingdomMembershipService.updateKingdomMembership(id, kingdomMembership);
        return ResponseEntity.ok(new ApiResponse("KingdomMembership updated successfully"));
    }

    @DeleteMapping("/leave/{kingdomId}")
    public ResponseEntity<?> deleteKingdomMembership(@AuthenticationPrincipal CustomUserDetails me,@PathVariable Integer kingdomId) {
        kingdomMembershipService.leaveKingdom(me.getId(), kingdomId);
        return ResponseEntity.ok(new ApiResponse("KingdomMembership deleted successfully"));
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<?> getKingdomMembershipById(@PathVariable Integer id) {
        return ResponseEntity.ok(kingdomMembershipService.getKingdomMembershipById(id));
    }

    // Resolve a player's membership id for a kingdom (for submit-image / submit-github / charity manual-donate).
    @GetMapping("/{kingdomId}/membership-id")
    public ResponseEntity<?> getMembershipId(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Integer kingdomId) {
        return ResponseEntity.ok(java.util.Map.of("membershipId", kingdomMembershipService.getMembershipId(me.getId(), kingdomId)));
    }

    //Shahad endpoint
    @GetMapping("/{kingdomId}/member-xp")
    public ResponseEntity<?> memberXp(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Integer kingdomId) {
        return ResponseEntity.ok(new ApiResponse(kingdomMembershipService.memberXp(me.getId(), kingdomId)));
    }

    //Shahad endpoint
    @GetMapping("/{kingdomId}/member-streak")
    public ResponseEntity<?> memberStreak(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Integer kingdomId) {
        return ResponseEntity.ok(new ApiResponse(kingdomMembershipService.memberStreak(me.getId(), kingdomId)));
    }

    //Shahad endpoint
    @GetMapping("/{kingdomId}/member-divison")
    public ResponseEntity<?> memberDivision(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Integer kingdomId) {
        return ResponseEntity.ok(new ApiResponse(kingdomMembershipService.memberDivision(me.getId(), kingdomId)));
    }

    //Shahad endpoint
    @GetMapping("/{kingdomId}/member-rank")
    public ResponseEntity<?> memberRank(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Integer kingdomId) {
        return ResponseEntity.ok(new ApiResponse(kingdomMembershipService.memberRank(me.getId(), kingdomId)));
    }

    //Shahad endpoint
    @GetMapping("/{kingdomId}/member-land-percentage")
    public ResponseEntity<?> memberLandPercentage(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Integer kingdomId) {
        return ResponseEntity.ok(new ApiResponse(kingdomMembershipService.memberPercentage(me.getId(), kingdomId)));
    }

    //Shahad endpoint
    @GetMapping("/{kingdomId}/xp-need-to-higher-rank")
    public ResponseEntity<?> xpNeededForHigherRank(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Integer kingdomId) {
        return ResponseEntity.ok(new ApiResponse(kingdomMembershipService.xpNeededForHigherRank(me.getId(), kingdomId)));
    }

    //Shahad endpoint
    @GetMapping("/{kingdomId}/number-of-completed-challenges")
    public ResponseEntity<?> numberOfCompletedChallenges(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Integer kingdomId) {
        return ResponseEntity.ok(kingdomMembershipService.numberOfCompletedChallenges(me.getId(), kingdomId));
    }

    //Shahad endpoint
    @GetMapping("/{kingdomId}/division-progress")
    public ResponseEntity<?> divisionProgress(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Integer kingdomId) {
        return ResponseEntity.ok(new ApiResponse(kingdomMembershipService.divisionProgress(me.getId(), kingdomId)));
    }
}
