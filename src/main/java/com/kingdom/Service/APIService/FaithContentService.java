package com.kingdom.Service.APIService;
import com.kingdom.API.ApiException;
import com.kingdom.DTO.OUT.IslamicContentDTO;
import com.kingdom.Enums.Difficulty;
import com.kingdom.Enums.Period;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FaithContentService {
    private final AlQuranCloudService alQuranCloudService;
    private final QuranTafsirService quranTafseerService;

    public List<IslamicContentDTO> findSuitableIslamicContents(
            Difficulty difficulty,
            Period period,
            List<String> existingReferences
    ) {

        List<Integer> candidateSurahs = new ArrayList<>(getCandidateSurahs(difficulty, period));

        Collections.shuffle(candidateSurahs);

        for (Integer surahNumber : candidateSurahs) {

            AlQuranCloudService.SurahData surah =
                    alQuranCloudService.getSurah(surahNumber);

            String reference = surah.englishName()
                    + " "
                    + surah.surahNumber()
                    + ":"
                    + surah.fromAyah()
                    + "-"
                    + surah.toAyah();

            if (isAlreadyUsed(reference, existingReferences)) {
                continue;
            }

            String tafseer = quranTafseerService.getSurahTafseer(
                    surah.surahNumber(),
                    surah.fromAyah(),
                    surah.toAyah()
            );

            IslamicContentDTO content = new IslamicContentDTO(
                    reference,
                    surah.arabicName(),
                    surah.surahNumber(),
                    surah.fromAyah(),
                    surah.toAyah(),
                    surah.ayahCount(),
                    surah.revelationType(),
                    surah.arabicText(),
                    shorten(tafseer, 1200),
                    shorten(tafseer, 500)
            );

            // نرجع سورة واحدة فقط للـ AI
            return List.of(content);
        }

        throw new ApiException("No suitable non-repeated Islamic content found for this difficulty and period");
    }

    private List<Integer> getCandidateSurahs(Difficulty difficulty, Period period) {

        return switch (period) {

            case DAILY -> switch (difficulty) {

                case EASY -> List.of(
                        112, // الإخلاص
                        108, // الكوثر
                        103, // العصر
                        110, // النصر
                        106, // قريش
                        109, // الكافرون
                        105, // الفيل
                        111  // المسد
                );

                case MEDIUM -> List.of(
                        1,   // الفاتحة
                        113, // الفلق
                        114, // الناس
                        107, // الماعون
                        94,  // الشرح
                        95,  // التين
                        97,  // القدر
                        99   // الزلزلة
                );

                case HARD -> List.of(
                        104, // الهمزة
                        93,  // الضحى
                        102, // التكاثر
                        101, // القارعة
                        100, // العاديات
                        91,  // الشمس
                        92,  // الليل
                        96   // العلق
                );
            };

            case WEEKLY -> switch (difficulty) {

                case EASY -> List.of(
                        86, // الطارق
                        87, // الأعلى
                        85, // البروج
                        84, // الانشقاق
                        82, // الانفطار
                        81, // التكوير
                        83, // المطففين
                        90  // البلد
                );

                case MEDIUM -> List.of(
                        88, // الغاشية
                        89, // الفجر
                        78, // النبأ
                        79, // النازعات
                        80, // عبس
                        77, // المرسلات
                        75, // القيامة
                        76  // الإنسان
                );

                case HARD -> List.of(
                        67, // الملك
                        68, // القلم
                        69, // الحاقة
                        70, // المعارج
                        53, // النجم
                        54, // القمر
                        56, // الواقعة
                        55  // الرحمن
                );
            };

            case MONTHLY -> switch (difficulty) {

                case EASY -> List.of(
                        36, // يس
                        44, // الدخان
                        45, // الجاثية
                        46, // الأحقاف
                        50, // ق
                        51, // الذاريات
                        52, // الطور
                        57  // الحديد
                );

                case MEDIUM -> List.of(
                        18, // الكهف
                        12, // يوسف
                        19, // مريم
                        20, // طه
                        21, // الأنبياء
                        23, // المؤمنون
                        24, // النور
                        25  // الفرقان
                );

                case HARD -> List.of(
                        3,  // آل عمران
                        4,  // النساء
                        5,  // المائدة
                        6,  // الأنعام
                        7,  // الأعراف
                        9,  // التوبة
                        10, // يونس
                        11, // هود
                        14, // إبراهيم
                        16, // النحل
                        17  // الإسراء
                );
            };

            default -> throw new ApiException("Unsupported period");
        };
    }

    private boolean isAlreadyUsed(String reference, List<String> existingReferences) {

        if (existingReferences == null || existingReferences.isEmpty()) {
            return false;
        }

        String normalizedReference = normalize(reference);

        for (String existing : existingReferences) {

            if (existing == null || existing.isBlank()) {
                continue;
            }

            String normalizedExisting = normalize(existing);

            if (normalizedExisting.equals(normalizedReference)) {
                return true;
            }

            if (normalizedExisting.contains(normalizedReference)
                    || normalizedReference.contains(normalizedExisting)) {
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
                .replaceAll("[^a-z0-9\\u0600-\\u06FF: -]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String shorten(String text, int maxLength) {

        if (text == null) {
            return "";
        }

        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength) + "...";
    }
}
