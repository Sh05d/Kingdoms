package com.kingdom.Controller;
import com.kingdom.API.ApiResponse;
import com.kingdom.Model.ChallengeQuestion;
import com.kingdom.Service.APIService.ChallengeQuestionWhatsappService;
import com.kingdom.Service.ChallengeQuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

//this page for admin only
@RestController
@RequestMapping("/api/v1/challenge-question")
@RequiredArgsConstructor
public class ChallengeQuestionController {
    private final ChallengeQuestionService challengeQuestionService;
    private final ChallengeQuestionWhatsappService challengeQuestionWhatsappService;

    @GetMapping("/get")
    public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(challengeQuestionService.getAllChallengeQuestions());
    }

    @PostMapping("/add")
    public ResponseEntity<?> add(@RequestBody @Valid ChallengeQuestion challengeQuestion) {
        challengeQuestionService.addChallengeQuestion(challengeQuestion);
        return ResponseEntity.ok(new ApiResponse("Challenge question created successfully"));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> update(
            @PathVariable Integer id,
            @RequestBody @Valid ChallengeQuestion challengeQuestion
    ) {
        challengeQuestionService.updateChallengeQuestion(id, challengeQuestion);
        return ResponseEntity.ok(new ApiResponse("Challenge question updated successfully"));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        challengeQuestionService.deleteChallengeQuestion(id);
        return ResponseEntity.ok(new ApiResponse("Challenge question deleted successfully"));
    }

    @PostMapping("/whatsapp/webhook")
    public ResponseEntity<?> receiveWhatsappReply(@RequestParam Map<String, String> params) {

        String from = params.get("From");
        String body = params.get("Body");

        String response = challengeQuestionWhatsappService.handleIncomingAnswer(from, body);

        return ResponseEntity.ok(Map.of("message", response));
    }
}
