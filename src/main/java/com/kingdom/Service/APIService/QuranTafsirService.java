package com.kingdom.Service.APIService;

import com.kingdom.API.ApiException;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.http.*;

import java.util.List;


@Service
@RequiredArgsConstructor
public class QuranTafsirService {

    @Value("${quran.tafseer.base-url:http://api.quran-tafseer.com}")
    private String baseUrl;

    @Value("${quran.tafseer.id:1}")
    private Integer tafseerId;

    private final RestTemplate restTemplate;

    public String getSurahTafseer(Integer surahNumber, Integer fromAyah, Integer toAyah) {

        StringBuilder tafseer = new StringBuilder();

        for (int ayah = fromAyah; ayah <= toAyah; ayah++) {

            String ayahTafseer = getAyahTafseer(surahNumber, ayah);

            if (!ayahTafseer.isBlank()) {
                tafseer
                        .append("آية ")
                        .append(ayah)
                        .append(": ")
                        .append(ayahTafseer)
                        .append(" ");
            }
        }

        String result = tafseer.toString().trim();

        if (result.isBlank()) {
            throw new ApiException("No tafseer returned from Quran Tafseer API");
        }

        return result;
    }

    private String getAyahTafseer(Integer surahNumber, Integer ayahNumber) {

        String url = getSafeBaseUrl()
                + "/tafseer/"
                + tafseerId
                + "/"
                + surahNumber
                + "/"
                + ayahNumber;

        try {
            String raw = restTemplate.getForObject(url, String.class);

            if (raw == null || raw.isBlank()) {
                return "";
            }

            JsonNode root = new ObjectMapper().readTree(raw);

            String text = extractText(root);

            return cleanText(text);

        } catch (Exception e) {
            throw new ApiException("Failed to call Quran Tafseer API: " + e.getMessage());
        }
    }

    private String extractText(JsonNode root) {

        if (root == null || root.isMissingNode()) {
            return "";
        }

        if (root.has("text")) {
            return root.path("text").asText("");
        }

        if (root.has("tafseer")) {
            return root.path("tafseer").asText("");
        }

        if (root.has("tafsir")) {
            return root.path("tafsir").asText("");
        }

        if (root.has("translation")) {
            return root.path("translation").asText("");
        }

        return "";
    }

    private String getSafeBaseUrl() {

        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ApiException("Quran Tafseer base URL is missing");
        }

        String fixedBaseUrl = baseUrl.trim();

        if (!fixedBaseUrl.startsWith("http://") && !fixedBaseUrl.startsWith("https://")) {
            fixedBaseUrl = "http://" + fixedBaseUrl;
        }

        if (fixedBaseUrl.endsWith("/")) {
            fixedBaseUrl = fixedBaseUrl.substring(0, fixedBaseUrl.length() - 1);
        }

        return fixedBaseUrl;
    }

    private String cleanText(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
