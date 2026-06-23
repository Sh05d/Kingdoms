package com.kingdom.Controller;

import com.kingdom.API.ApiResponse;
import com.kingdom.Config.CustomUserDetails;
import com.kingdom.DTO.IN.ChallengeProgressIn;
import com.kingdom.DTO.IN.GithubSubmissionIn;
import com.kingdom.DTO.IN.NutritionTextIn;
import com.kingdom.DTO.OUT.ChallengeProgressOut;
import com.kingdom.Enums.ProgressStatus;
import com.kingdom.Service.ChallengeProgressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/challenge-progress")
@RequiredArgsConstructor
public class ChallengeProgressController {

    private final ChallengeProgressService challengeProgressService;

    // Reads return clean ChallengeProgressOut DTOs (never the raw entity).

    @GetMapping("/get")
    public List<ChallengeProgressOut> getAllChallengeProgresses() {
        return ChallengeProgressOut.fromList(challengeProgressService.getAllChallengeProgresses());
    }

    @PostMapping("/add")
    public ApiResponse addChallengeProgress(@Valid @RequestBody ChallengeProgressIn challengeProgress) {
        challengeProgressService.addChallengeProgress(challengeProgress);
        return new ApiResponse("ChallengeProgress added successfully");
    }

    @PutMapping("/update/{id}")
    public ApiResponse updateChallengeProgress(@PathVariable Integer id,
                                               @Valid @RequestBody ChallengeProgressIn challengeProgress) {
        challengeProgressService.updateChallengeProgress(id, challengeProgress);
        return new ApiResponse("ChallengeProgress updated successfully");
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse deleteChallengeProgress(@PathVariable Integer id) {
        challengeProgressService.deleteChallengeProgress(id);
        return new ApiResponse("ChallengeProgress deleted successfully");
    }

    @GetMapping("/get/{id}")
    public ChallengeProgressOut getChallengeProgressById(@PathVariable Integer id) {
        return ChallengeProgressOut.from(challengeProgressService.getChallengeProgressById(id));
    }

    // ---- Challenge lifecycle: join -> finish ----

    @PostMapping("/join/{challengeId}")
    public ResponseEntity<?> joinChallenge(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Integer challengeId) {
        return ResponseEntity.ok(challengeProgressService.joinChallenge(me.getId(), challengeId));
    }

    @PostMapping("/finish/{id}")
    public ApiResponse finishChallenge(@PathVariable Integer id) {
        return new ApiResponse(challengeProgressService.finishChallenge(id));
    }

    @PostMapping("/cancel/{id}")
    public ApiResponse cancelChallenge(@PathVariable Integer id) {
        challengeProgressService.cancelChallenge(id);
        return new ApiResponse("Challenge canceled successfully");
    }

    @GetMapping("/player")
    public List<ChallengeProgressOut> getProgressByPlayer(@AuthenticationPrincipal CustomUserDetails me) {
        return ChallengeProgressOut.fromList(challengeProgressService.getProgressByPlayer(me.getId()));
    }

    @GetMapping("/player/active")
    public List<ChallengeProgressOut> getActiveByPlayer(@AuthenticationPrincipal CustomUserDetails me) {
        return ChallengeProgressOut.fromList(challengeProgressService.getActiveByPlayer(me.getId()));
    }

    @GetMapping("/player/status/{status}")
    public List<ChallengeProgressOut> getProgressByPlayerAndStatus(@AuthenticationPrincipal CustomUserDetails me,
                                                                   @PathVariable ProgressStatus status) {
        return ChallengeProgressOut.fromList(challengeProgressService.getProgressByPlayerAndStatus(me.getId(), status));
    }

    //ENDPOINT BY MAYSUN
    @PostMapping("/submit-github/{kingdomMembershipId}/{challengeId}")
    public ResponseEntity<?> submitGithubChallenge(@PathVariable Integer kingdomMembershipId, @PathVariable Integer challengeId, @RequestBody @Valid GithubSubmissionIn githubSubmissionIn) {
        String result = challengeProgressService.submitGithubChallenge(kingdomMembershipId, challengeId, githubSubmissionIn);
        return ResponseEntity.status(200).body(new ApiResponse(result));
    }
    @PostMapping("/submit-image/{progressId}")
    public ResponseEntity<?> submitNutritionImage(@PathVariable Integer progressId, @RequestParam("image") MultipartFile image) {
        String result = challengeProgressService.submitNutritionImage(progressId, image);
        return ResponseEntity.ok(new ApiResponse(result));
    }
}
