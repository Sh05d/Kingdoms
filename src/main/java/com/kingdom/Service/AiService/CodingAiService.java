package com.kingdom.Service.AiService;

import com.kingdom.Enums.Difficulty;
import com.kingdom.Enums.KingdomType;
import com.kingdom.Enums.Period;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
@RequiredArgsConstructor
public class CodingAiService implements com.kingdom.Service.AiService.KingdomAiService {

    private final com.kingdom.Service.AiService.OpenAiClient openAiClient;

    @Override
    public KingdomType kingdom() {
        return KingdomType.PROGRAMMING;
    }

    @Override
    public String generateChallenge(Difficulty difficulty, Period period, List<String> existingChallenges) {

        String instructions = """
                أنت مسؤول عن توليد تحديات لمملكة البرمجة في منصة Kingdom.

                أرجع JSON صحيح فقط.
                لا تستخدم Markdown.
                لا تضف أي شرح خارج JSON.

                القواعد:
                - التحدي يجب أن يكون عملياً وقابلاً للتنفيذ.
                - التحقق يتم عبر GitHub فقط: وجود Repository وفيه commits.
                - لا تطلب Screenshot أو Deploy أو تقييم يدوي.
                - لا تطلب Private Repository.
                - لا تكرر أي عنوان أو هدف من التحديات الحالية.
                - اجعل title و description باللغة العربية.
                - اجعل title قصيراً وجذاباً.
                - verificationSource يجب أن يكون دائماً "GITHUB".
                - verificationRule يجب أن يكون دائماً "REPOSITORY_COMMITS".
                - verificationTarget يجب أن يكون دائماً "GITHUB_REPOSITORY".

                قواعد الصعوبة:
                - EASY: دالة، سكربت بسيط، أو مسألة برمجية قصيرة.
                - MEDIUM: مشروع صغير مثل CRUD API، CLI Tool، أو تطبيق بسيط.
                - HARD: مشروع أكبر مثل Full Stack App، نظام متعدد الكلاسات، أو خوارزمية مع اختبارات.

                قواعد الفترة:
                - DAILY: تحدي صغير يمكن إنجازه خلال يوم.
                - WEEKLY: مشروع متوسط يحتاج عدة خطوات.
                - MONTHLY: مشروع أكبر بفكرة أوضح وهيكلة أكثر.
                """;

        String input = """
                الصعوبة: %s
                الفترة: %s

                عناوين أو أهداف تحديات مستخدمة مسبقاً ويجب تجنب تكرارها:
                %s

                أنشئ تحدياً واحداً فقط بهذا الشكل:

                {
                  "title": "ابنِ API بسيط لإدارة المهام",
                  "description": "أنشئ مشروعاً يحتوي على عمليات إضافة وعرض وتحديث وحذف للمهام، ثم ارفعه على GitHub مع commits واضحة.",
                  "targetName": "GitHub Repository",
                  "targetValue": 1,
                  "verificationSource": "GITHUB",
                  "verificationRule": "REPOSITORY_COMMITS",
                  "verificationTarget": "GITHUB_REPOSITORY"
                }

                قواعد الحقول:
                - targetName دائماً "GitHub Repository".
                - targetValue دائماً 1.
                - verificationSource دائماً "GITHUB".
                - verificationRule دائماً "REPOSITORY_COMMITS".
                - verificationTarget دائماً "GITHUB_REPOSITORY".
                - title و description بالعربية.
                - description يجب أن يوضح المطلوب برمجياً بدون شروط غير قابلة للتحقق آلياً.
                """.formatted(difficulty, period, existingChallenges);

        return openAiClient.generate(instructions, input);
    }
    }
