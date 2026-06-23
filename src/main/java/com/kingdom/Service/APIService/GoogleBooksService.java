package com.kingdom.Service.APIService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingdom.API.ApiException;
import com.kingdom.DTO.OUT.GoogleBookDTO;
import com.kingdom.Enums.Difficulty;
import com.kingdom.Enums.Period;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;


import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GoogleBooksService {

    @Value("${google.books.api.key:}")
    private String googleBooksApiKey;

    private final RestTemplate restTemplate;

    public List<GoogleBookDTO> findSuitableBooks(
            String query,
            Difficulty difficulty,
            Period period,
            List<String> existingBookNames
    ) {

        if (googleBooksApiKey == null || googleBooksApiKey.isBlank()) {
            throw new ApiException("Google Books API key is missing");
        }

        int minPages = getMinPages(difficulty, period);
        int maxPages = getMaxPages(difficulty, period);

        List<GoogleBookDTO> books = new ArrayList<>();
        Set<String> seenBookIds = new HashSet<>();
        Set<String> seenTitles = new HashSet<>();

        for (int startIndex : List.of(0, 40)) {

            String raw = callGoogleBooksWithRetry(
                    buildGoogleBooksUrl(query, startIndex)
            );

            try {
                JsonNode response = new ObjectMapper().readTree(raw);
                JsonNode items = response.path("items");

                if (!items.isArray() || items.isEmpty()) {
                    continue;
                }

                for (JsonNode item : items) {

                    JsonNode info = item.path("volumeInfo");

                    String googleBookId = item.path("id").asText("");
                    String title = info.path("title").asText("");
                    String description = info.path("description").asText("");
                    int pageCount = info.path("pageCount").asInt(0);

                    if (googleBookId.isBlank()) {
                        continue;
                    }

                    if (title.isBlank()) {
                        continue;
                    }

                    if (description.isBlank()) {
                        continue;
                    }

                    if (pageCount <= 0) {
                        continue;
                    }

                    if (pageCount < minPages || pageCount > maxPages) {
                        continue;
                    }

                    String normalizedTitle = normalize(title);

                    if (seenBookIds.contains(googleBookId)) {
                        continue;
                    }

                    if (seenTitles.contains(normalizedTitle)) {
                        continue;
                    }

                    if (isAlreadyUsed(title, existingBookNames)) {
                        continue;
                    }

                    List<String> authors = extractAuthors(info);

                    if (authors.isEmpty()) {
                        authors.add("Unknown");
                    }

                    seenBookIds.add(googleBookId);
                    seenTitles.add(normalizedTitle);

                    books.add(new GoogleBookDTO(
                            googleBookId,
                            title,
                            authors,
                            description,
                            pageCount
                    ));

                    if (books.size() == 10) {
                        return books;
                    }
                }

            } catch (ApiException e) {
                throw e;
            } catch (Exception e) {
                throw new ApiException("Failed to parse Google Books response: " + e.getMessage());
            }
        }

        if (books.isEmpty()) {
            throw new ApiException("No suitable non-repeated books found for this difficulty and period");
        }

        return books;
    }

    private String buildGoogleBooksUrl(String query, int startIndex) {

        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

        return "https://www.googleapis.com/books/v1/volumes"
                + "?q=" + encodedQuery
                + "&maxResults=40"
                + "&startIndex=" + startIndex
                + "&printType=books"
                + "&key=" + googleBooksApiKey;
    }

    private List<String> extractAuthors(JsonNode info) {

        List<String> authors = new ArrayList<>();

        JsonNode authorsNode = info.path("authors");

        if (authorsNode.isArray()) {
            for (JsonNode author : authorsNode) {
                String authorName = author.asText("");

                if (!authorName.isBlank()) {
                    authors.add(authorName);
                }
            }
        }

        return authors;
    }

    private boolean isAlreadyUsed(String title, List<String> existingBookNames) {

        if (existingBookNames == null || existingBookNames.isEmpty()) {
            return false;
        }

        String normalizedTitle = normalize(title);

        for (String existing : existingBookNames) {

            if (existing == null || existing.isBlank()) {
                continue;
            }

            String normalizedExisting = normalize(existing);

            if (normalizedExisting.equals(normalizedTitle)) {
                return true;
            }

            if (normalizedExisting.contains(normalizedTitle)
                    || normalizedTitle.contains(normalizedExisting)) {
                return true;
            }
        }

        return false;
    }

    private String normalize(String value) {

        if (value == null) {
            return "";
        }

        return value
                .toLowerCase()
                .replaceAll("[^a-z0-9\\u0600-\\u06FF ]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String callGoogleBooksWithRetry(String url) {

        int maxAttempts = 3;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {

            try {
                String raw = restTemplate.getForObject(url, String.class);

                if (raw == null || raw.isBlank()) {
                    throw new ApiException("Google Books returned empty response");
                }

                return raw;

            } catch (HttpServerErrorException e) {

                if (e.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {

                    if (attempt == maxAttempts) {
                        throw new ApiException("Google Books service is temporarily unavailable. Please try again later.");
                    }

                    sleepBeforeRetry();
                    continue;
                }

                throw new ApiException("Google Books server error: " + e.getStatusCode());

            } catch (ApiException e) {
                throw e;

            } catch (Exception e) {
                throw new ApiException("Failed to call Google Books API: " + e.getMessage());
            }
        }

        throw new ApiException("Google Books service is temporarily unavailable. Please try again later.");
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("Request interrupted while retrying Google Books API");
        }
    }

    private int getMinPages(Difficulty difficulty, Period period) {

        return switch (period) {

            case DAILY -> switch (difficulty) {
                case EASY -> 20;
                case MEDIUM -> 40;
                case HARD -> 60;
            };

            case WEEKLY -> switch (difficulty) {
                case EASY -> 80;
                case MEDIUM -> 140;
                case HARD -> 220;
            };

            case MONTHLY -> switch (difficulty) {
                case EASY -> 200;
                case MEDIUM -> 350;
                case HARD -> 500;
            };

            default -> throw new ApiException("Unsupported period");
        };
    }

    private int getMaxPages(Difficulty difficulty, Period period) {

        return switch (period) {

            case DAILY -> switch (difficulty) {
                case EASY -> 50;
                case MEDIUM -> 80;
                case HARD -> 100;
            };

            case WEEKLY -> switch (difficulty) {
                case EASY -> 160;
                case MEDIUM -> 260;
                case HARD -> 380;
            };

            case MONTHLY -> switch (difficulty) {
                case EASY -> 350;
                case MEDIUM -> 550;
                case HARD -> 800;
            };

            default -> throw new ApiException("Unsupported period");
        };
    }
}
