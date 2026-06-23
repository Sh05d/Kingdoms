package com.kingdom.Controller;

import com.kingdom.API.ApiResponse;
import com.kingdom.Config.CustomUserDetails;
import com.kingdom.DTO.IN.KingdomIN;
import com.kingdom.DTO.IN.KingdomRecommendationIn;
import com.kingdom.Enums.Period;
import com.kingdom.Model.Kingdom;
import com.kingdom.Service.KingdomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/kingdom")
@RequiredArgsConstructor
public class KingdomController {

    private final KingdomService kingdomService;

    @GetMapping("/get")
    public ResponseEntity<?> getAllKingdoms() {
        return ResponseEntity.ok(kingdomService.getAllKingdoms());
    }

    @PostMapping("/add")
    public ResponseEntity<?> addKingdom(@RequestBody @Valid KingdomIN kingdom) {
        kingdomService.addKingdom(kingdom);
        return ResponseEntity.ok(new ApiResponse("Kingdom added successfully"));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateKingdom(@PathVariable Integer id, @RequestBody @Valid KingdomIN kingdom) {
        kingdomService.updateKingdom(id, kingdom);
        return ResponseEntity.ok(new ApiResponse("Kingdom updated successfully"));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteKingdom(@PathVariable Integer id) {
        kingdomService.deleteKingdom(id);
        return ResponseEntity.ok(new ApiResponse("Kingdom deleted successfully"));
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<?> getKingdomById(@PathVariable Integer id) {
        return ResponseEntity.ok(kingdomService.getKingdomById(id));
    }

    //Shahad endpoint
    @GetMapping("/{kingdomId}/land-control/{division}")
    public ResponseEntity<?> landControlSummary(@PathVariable Integer kingdomId,@PathVariable Integer division) {
        return ResponseEntity.ok(kingdomService.landControlSummary(kingdomId, division));
    }

    //Shahad endpoint
    @GetMapping("/{kingdomId}/leaderboard/period/{period}")
    public ResponseEntity<?> leaderboardByPeriod(@PathVariable Integer kingdomId, @PathVariable Period period) {
        return ResponseEntity.ok(kingdomService.leaderboardByPeriod(kingdomId, period));
    }

    //Shahad endpoint
    @GetMapping("/{kingdomId}/leaderboard/period/{period}/division/{division}")
    public ResponseEntity<?> leaderboardByPeriodAndDivision(@PathVariable Integer kingdomId, @PathVariable Period period, @PathVariable Integer division) {
        return ResponseEntity.ok(kingdomService.leaderboardByPeriodAndDivision(kingdomId, division, period));
    }

    //Shahad endpoint
    @GetMapping("/{kingdomId}/leaderboard/division/{division}")
    public ResponseEntity<?> leaderboardByDivision(@PathVariable Integer kingdomId, @PathVariable Integer division) {
        return ResponseEntity.ok(kingdomService.leaderboardByDivision(kingdomId, division));
    }

    //Shahad endpoint
    @PostMapping("/ai-recommendation")
    public ResponseEntity<?> recommendKingdom(@AuthenticationPrincipal CustomUserDetails me, @RequestBody @Valid KingdomRecommendationIn recommendationIN) {
        return ResponseEntity.ok(kingdomService.recommendKingdom(me.getId(), recommendationIN));
    }
}
