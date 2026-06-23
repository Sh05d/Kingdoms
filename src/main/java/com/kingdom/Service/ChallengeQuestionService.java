package com.kingdom.Service;

import com.kingdom.API.ApiException;
import com.kingdom.Model.Challenge;
import com.kingdom.Model.ChallengeQuestion;
import com.kingdom.Repository.ChallengeQuestionRepository;
import com.kingdom.Repository.ChallengeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChallengeQuestionService {

    private final ChallengeQuestionRepository challengeQuestionRepository;
    private final ChallengeRepository challengeRepository;

    // ---- Shahad: quiz CRUD + AI question generation (Reading/Faith) ----

    public List<ChallengeQuestion> getAllChallengeQuestions() {
        return challengeQuestionRepository.findAll();
    }

    public void addChallengeQuestion(ChallengeQuestion challengeQuestion) {
        Challenge challenge = challengeRepository.findChallengeById(challengeQuestion.getChallenge().getId());
        if (challenge == null) {
            throw new ApiException("Challenge not found");
        }
        validateCorrectAnswer(challengeQuestion.getCorrectAnswer());

        ChallengeQuestion question = new ChallengeQuestion();
        question.setQuestion(challengeQuestion.getQuestion());
        question.setOptionA(challengeQuestion.getOptionA());
        question.setOptionB(challengeQuestion.getOptionB());
        question.setOptionC(challengeQuestion.getOptionC());
        question.setOptionD(challengeQuestion.getOptionD());
        question.setCorrectAnswer(challengeQuestion.getCorrectAnswer());
        question.setChallenge(challenge);
        challengeQuestionRepository.save(question);
    }

    public void updateChallengeQuestion(Integer id, ChallengeQuestion challengeQuestion) {
        ChallengeQuestion oldQuestion = findChallengeQuestionById(id);
        Challenge challenge = challengeRepository.findChallengeById(challengeQuestion.getChallenge().getId());
        if (challenge == null) {
            throw new ApiException("Challenge not found");
        }
        validateCorrectAnswer(challengeQuestion.getCorrectAnswer());

        oldQuestion.setQuestion(challengeQuestion.getQuestion());
        oldQuestion.setOptionA(challengeQuestion.getOptionA());
        oldQuestion.setOptionB(challengeQuestion.getOptionB());
        oldQuestion.setOptionC(challengeQuestion.getOptionC());
        oldQuestion.setOptionD(challengeQuestion.getOptionD());
        oldQuestion.setCorrectAnswer(challengeQuestion.getCorrectAnswer());
        oldQuestion.setChallenge(challenge);
        challengeQuestionRepository.save(oldQuestion);
    }

    public void deleteChallengeQuestion(Integer id) {
        ChallengeQuestion question = findChallengeQuestionById(id);
        challengeQuestionRepository.delete(question);
    }

    public ChallengeQuestion findChallengeQuestionById(Integer id) {
        ChallengeQuestion question = challengeQuestionRepository.findChallengeQuestionById(id);
        if (question == null) {
            throw new ApiException("Challenge question not found");
        }
        return question;
    }

    private void validateCorrectAnswer(String correctAnswer) {
        if (!List.of("A", "B", "C", "D").contains(correctAnswer)) {
            throw new ApiException("Correct answer must be A, B, C, or D");
        }
    }

    // Create the 5 quiz questions from the AI's JSON (used by ChallengeService when generating a Reading/Faith challenge).
    public void saveChallengeQuestions(JsonNode node, Challenge challenge) {
        if (!node.has("questions") || !node.get("questions").isArray()) {
            throw new ApiException("Challenge must contain questions");
        }
        JsonNode questions = node.get("questions");
        if (questions.size() != 5) {
            throw new ApiException("Challenge must contain exactly 5 questions");
        }
        for (JsonNode q : questions) {
            validateQuestionNode(q);
            ChallengeQuestion question = new ChallengeQuestion();
            question.setQuestion(q.get("question").asText());
            question.setOptionA(q.get("optionA").asText());
            question.setOptionB(q.get("optionB").asText());
            question.setOptionC(q.get("optionC").asText());
            question.setOptionD(q.get("optionD").asText());
            String correctAnswer = q.get("correctAnswer").asText();
            if (!List.of("A", "B", "C", "D").contains(correctAnswer)) {
                throw new ApiException("Question correctAnswer must be A, B, C, or D");
            }
            question.setCorrectAnswer(correctAnswer);
            question.setChallenge(challenge);
            challengeQuestionRepository.save(question);
        }
    }

    private void validateQuestionNode(JsonNode q) {
        if (!q.has("question") || q.get("question").asText().isBlank()) {
            throw new ApiException("Question text is missing");
        }
        if (!q.has("optionA") || q.get("optionA").asText().isBlank()) {
            throw new ApiException("Question optionA is missing");
        }
        if (!q.has("optionB") || q.get("optionB").asText().isBlank()) {
            throw new ApiException("Question optionB is missing");
        }
        if (!q.has("optionC") || q.get("optionC").asText().isBlank()) {
            throw new ApiException("Question optionC is missing");
        }
        if (!q.has("optionD") || q.get("optionD").asText().isBlank()) {
            throw new ApiException("Question optionD is missing");
        }
        if (!q.has("correctAnswer") || q.get("correctAnswer").asText().isBlank()) {
            throw new ApiException("Question correctAnswer is missing");
        }
    }

    // ---- Maysun: alternate question CRUD addressed by challenge id ----

    public List<ChallengeQuestion> getAllQuestions() {
        return challengeQuestionRepository.findAll();
    }

    public List<ChallengeQuestion> getQuestionsByChallenge(Integer challengeId) {
        Challenge challenge = findChallengeById(challengeId);
        return challengeQuestionRepository.findAllByChallengeId(challenge.getId());
    }

    public void addQuestion(Integer challengeId, ChallengeQuestion question) {
        Challenge challenge = findChallengeById(challengeId);
        ChallengeQuestion newQuestion = new ChallengeQuestion();
        newQuestion.setQuestion(question.getQuestion());
        newQuestion.setOptionA(question.getOptionA());
        newQuestion.setOptionB(question.getOptionB());
        newQuestion.setOptionC(question.getOptionC());
        newQuestion.setOptionD(question.getOptionD());
        newQuestion.setCorrectAnswer(question.getCorrectAnswer());
        newQuestion.setChallenge(challenge);
        challengeQuestionRepository.save(newQuestion);
    }

    public void updateQuestion(Integer id, ChallengeQuestion question) {
        ChallengeQuestion oldQuestion = findQuestionById(id);
        oldQuestion.setQuestion(question.getQuestion());
        oldQuestion.setOptionA(question.getOptionA());
        oldQuestion.setOptionB(question.getOptionB());
        oldQuestion.setOptionC(question.getOptionC());
        oldQuestion.setOptionD(question.getOptionD());
        oldQuestion.setCorrectAnswer(question.getCorrectAnswer());
        challengeQuestionRepository.save(oldQuestion);
    }

    public void deleteQuestion(Integer id) {
        ChallengeQuestion question = findQuestionById(id);
        challengeQuestionRepository.delete(question);
    }

    public ChallengeQuestion getQuestionById(Integer id) {
        return findQuestionById(id);
    }

    private ChallengeQuestion findQuestionById(Integer id) {
        ChallengeQuestion question = challengeQuestionRepository.findChallengeQuestionById(id);
        if (question == null) {
            throw new ApiException("Challenge question not found");
        }
        return question;
    }

    private Challenge findChallengeById(Integer id) {
        Challenge challenge = challengeRepository.findChallengeById(id);
        if (challenge == null) {
            throw new ApiException("Challenge not found");
        }
        return challenge;
    }
}
