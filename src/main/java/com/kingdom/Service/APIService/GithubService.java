package com.kingdom.Service.APIService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingdom.API.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.OffsetDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GithubService {

    @Value("${github.api.token:}")
    private String githubToken;

    private static final Pattern GITHUB_URL_PATTERN = Pattern.compile("github\\.com/([^/]+)/([^/\\s?#]+?)(?:\\.git)?/?(?:[?#].*)?$");

    public boolean verifyRepository(String repoUrl) {

        String[] ownerAndRepo = extractOwnerAndRepo(repoUrl);
        String owner = ownerAndRepo[0];
        String repo = ownerAndRepo[1];

        WebClient.Builder builder = WebClient.builder()
                .baseUrl("https://api.github.com").defaultHeader("Accept", "application/vnd.github+json");

        if (githubToken != null && !githubToken.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + githubToken);
        }

        WebClient client = builder.build();

        try {
            client.get()
                    .uri("/repos/{owner}/{repo}", owner, repo)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

        } catch (WebClientResponseException.NotFound e) {
            throw new ApiException("GitHub repository not found");
        } catch (Exception e) {
            throw new ApiException("Failed to reach GitHub: " + e.getMessage());
        }

        try {
            String response = client.get()
                    .uri("/repos/{owner}/{repo}/commits?per_page=1", owner, repo)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode commits = mapper.readTree(response);

            return commits.isArray() && commits.size() > 0;

        } catch (WebClientResponseException.Conflict e) {
            return false;
        } catch (Exception e) {
            throw new ApiException("Failed to verify commits: " + e.getMessage());
        }
    }

    private String[] extractOwnerAndRepo(String repoUrl) {

        Matcher matcher = GITHUB_URL_PATTERN.matcher(repoUrl);

        if (!matcher.find()) {
            throw new ApiException("Invalid GitHub repository URL");
        }

        return new String[]{matcher.group(1), matcher.group(2)};
    }

    public boolean verifyRecentCommit(String repoUrl) {

        String[] ownerAndRepo = extractOwnerAndRepo(repoUrl);
        String owner = ownerAndRepo[0];
        String repo = ownerAndRepo[1];

        WebClient.Builder builder = WebClient.builder().baseUrl("https://api.github.com").defaultHeader("Accept", "application/vnd.github+json");

        if (githubToken != null && !githubToken.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + githubToken);
        }

        WebClient client = builder.build();

        try {
            String response = client.get()
                    .uri("/repos/{owner}/{repo}/commits?per_page=1", owner, repo).retrieve().bodyToMono(String.class).block();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode commits = mapper.readTree(response);

            if (!commits.isArray() || commits.isEmpty()) {
                return false;
            }

            String commitDate = commits.get(0)
                    .path("commit")
                    .path("committer")
                    .path("date")
                    .asText();

            if (commitDate == null || commitDate.isBlank()) {
                return false;
            }

            OffsetDateTime lastCommitDate = OffsetDateTime.parse(commitDate);
            OffsetDateTime sevenDaysAgo = OffsetDateTime.now().minusDays(7);

            return lastCommitDate.isAfter(sevenDaysAgo);

        } catch (WebClientResponseException.NotFound e) {
            throw new ApiException("GitHub repository not found");
        } catch (Exception e) {
            throw new ApiException("Failed to verify recent GitHub commit: " + e.getMessage());
        }
    }
}
