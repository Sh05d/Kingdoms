package com.kingdom.DTO.OUT;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IslamicContentDTO {
    private String reference;        // Al-Ikhlas 112:1-4

    private String surahName;        // الإخلاص

    private Integer surahNumber;     // 112

    private Integer fromAyah;        // 1

    private Integer toAyah;          // 4

    private Integer ayahCount;       // 4

    private String revelationType;   // MAKKI / MADANI

    private String arabicText;       // نص الآيات

    private String tafsirSummary;    // التفسير

    private String mainMeaning;      // مختصر من التفسير
}
