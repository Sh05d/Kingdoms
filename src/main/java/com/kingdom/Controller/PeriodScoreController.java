package com.kingdom.Controller;
import com.kingdom.API.ApiResponse;
import com.kingdom.Model.KingdomMembership;
import com.kingdom.Model.PeriodScore;
import com.kingdom.Service.PeriodScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/period-score")
@RequiredArgsConstructor
public class PeriodScoreController {
    private final PeriodScoreService periodScoreService;

    @GetMapping("/get")
    public ResponseEntity<?> getAllPeriodScores() {
        return ResponseEntity.ok(periodScoreService.getAllPeriodScores());
    }

    @PostMapping("/add/{membershipId}")
    public ResponseEntity<?> addPeriodScore(@PathVariable Integer membershipId, @RequestBody PeriodScore periodScore) {
        periodScoreService.addPeriodScore(membershipId, periodScore);
        return ResponseEntity.ok(new ApiResponse("Period score added successfully"));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> updatePeriodScore(@PathVariable Integer id, @RequestBody PeriodScore periodScore) {
        periodScoreService.updatePeriodScore(id, periodScore);
        return ResponseEntity.ok(new ApiResponse("Period score updated successfully"));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deletePeriodScore(@PathVariable Integer id) {
        periodScoreService.deletePeriodScore(id);
        return ResponseEntity.ok(new ApiResponse("Period score deleted successfully"));
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<?> getPeriodScoreById(@PathVariable Integer id) {
        return ResponseEntity.ok(periodScoreService.getPeriodScoreById(id));
    }
}
