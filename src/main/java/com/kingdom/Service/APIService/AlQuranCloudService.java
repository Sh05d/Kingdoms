package com.kingdom.Service.APIService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingdom.API.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;

import org.springframework.web.client.RestTemplate;


@Service
@RequiredArgsConstructor
public class AlQuranCloudService {
    @Value("${alquran.cloud.base-url:https://api.alquran.cloud/v1}")
    private String baseUrl;

    private final RestTemplate restTemplate;

    public SurahData getSurah(Integer surahNumber) {

        String url = baseUrl + "/surah/" + surahNumber + "/quran-uthmani";

        try {
            String raw = restTemplate.getForObject(url, String.class);

            if (raw == null || raw.isBlank()) {
                throw new ApiException("AlQuran Cloud returned empty response");
            }

            JsonNode root = new ObjectMapper().readTree(raw);

            if (!"OK".equalsIgnoreCase(root.path("status").asText())) {
                throw new ApiException("AlQuran Cloud request failed");
            }

            JsonNode data = root.path("data");

            String englishName = data.path("englishName").asText("");
            String arabicName = data.path("name").asText("");
            String revelationType = data.path("revelationType").asText("");
            JsonNode ayahs = data.path("ayahs");

            if (ayahs == null || !ayahs.isArray() || ayahs.isEmpty()) {
                throw new ApiException("No ayahs found from AlQuran Cloud");
            }

            StringBuilder arabicText = new StringBuilder();

            int fromAyah = ayahs.get(0).path("numberInSurah").asInt(1);
            int toAyah = fromAyah;

            for (JsonNode ayah : ayahs) {
                toAyah = ayah.path("numberInSurah").asInt(toAyah);

                String text = ayah.path("text").asText("");

                if (!text.isBlank()) {
                    arabicText.append(text).append(" ");
                }
            }

            String normalizedRevelationType =
                    "Medinan".equalsIgnoreCase(revelationType)
                            ? "MADANI"
                            : "MAKKI";

            return new SurahData(
                    surahNumber,
                    englishName,
                    arabicName,
                    fromAyah,
                    toAyah,
                    ayahs.size(),
                    normalizedRevelationType,
                    arabicText.toString().trim()
            );

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Failed to call AlQuran Cloud API: " + e.getMessage());
        }
    }

    public record SurahData(
            Integer surahNumber,
            String englishName,
            String arabicName,
            Integer fromAyah,
            Integer toAyah,
            Integer ayahCount,
            String revelationType,
            String arabicText
    ) {
    }
}
