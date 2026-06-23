package com.kingdom.Controller;

import com.kingdom.API.ApiResponse;
import com.kingdom.DTO.IN.ChallengeIn;
import com.kingdom.DTO.OUT.ChallengeOut;
import com.kingdom.Model.Challenge;
import com.kingdom.Enums.Difficulty;
import com.kingdom.Enums.Period;
import com.kingdom.Service.ChallengeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/challenge")
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeService challengeService;

    // Reads return clean ChallengeOut DTOs (never the raw entity).

    @GetMapping("/get")
    public List<ChallengeOut> getAllChallenges() {
        return ChallengeOut.fromList(challengeService.getAllChallenges());
    }

    @PostMapping("/add/{kingdomId}")
    public ApiResponse addChallenge(@PathVariable Integer kingdomId, @RequestBody Challenge challenge) {
        challengeService.addChallenge(kingdomId, challenge);
        return new ApiResponse("Challenge added successfully");
    }

    // AI GENERATES the challenge for a kingdom (you only pass the kingdom + difficulty + period).
    // Returns the generated + saved challenge. Needs openai.enabled=true + the kingdom to exist.
    @PostMapping("/generate")
    public ChallengeOut generateChallenge(@RequestParam Integer kingdomId,
                                          @RequestParam Difficulty difficulty,
                                          @RequestParam Period period) {
        return ChallengeOut.from(challengeService.generateChallenge(kingdomId, difficulty, period));
    }

    @PutMapping("/update/{id}")
    public ApiResponse updateChallenge(@PathVariable Integer id, @Valid @RequestBody ChallengeIn challenge) {
        challengeService.updateChallenge(id, challenge);
        return new ApiResponse("Challenge updated successfully");
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse deleteChallenge(@PathVariable Integer id) {
        challengeService.deleteChallenge(id);
        return new ApiResponse("Challenge deleted successfully");
    }

    @GetMapping("/get/{id}")
    public ChallengeOut getChallengeById(@PathVariable Integer id) {
        return ChallengeOut.from(challengeService.getChallengeById(id));
    }

    @GetMapping("/kingdom/{kingdomId}")
    public List<ChallengeOut> getChallengesByKingdom(@PathVariable Integer kingdomId) {
        return ChallengeOut.fromList(challengeService.getChallengesByKingdom(kingdomId));
    }

    @GetMapping("/difficulty/{difficulty}")
    public List<ChallengeOut> getChallengesByDifficulty(@PathVariable Difficulty difficulty) {
        return ChallengeOut.fromList(challengeService.getChallengesByDifficulty(difficulty));
    }

    @GetMapping("/period/{period}")
    public List<ChallengeOut> getChallengesByPeriod(@PathVariable Period period) {
        return ChallengeOut.fromList(challengeService.getChallengesByPeriod(period));
    }
}
