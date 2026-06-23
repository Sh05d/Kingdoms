package com.kingdom.Service.AiService;

import com.kingdom.Enums.Difficulty;
import com.kingdom.Enums.KingdomType;
import com.kingdom.Enums.Period;
import com.kingdom.DTO.OUT.GoogleBookDTO;
import com.kingdom.Service.APIService.GoogleBooksService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReadingAiService implements com.kingdom.Service.AiService.KingdomAiService {
    private final com.kingdom.Service.AiService.OpenAiClient openAiClient;
    private final GoogleBooksService googleBooksService;

    @Override
    public KingdomType kingdom() {
        return KingdomType.READING;
    }

    @Override
    public String generateChallenge(Difficulty difficulty, Period period, List<String> existingChallenges) {
        String searchQuery = buildSearchQuery(difficulty, period);

        List<GoogleBookDTO> books = googleBooksService.findSuitableBooks(
                searchQuery,
                difficulty,
                period,
                existingChallenges
        );

        String instructions = """
            أنت مسؤول عن توليد تحديات لمملكة القراءة في منصة Kingdom.

            أرجع JSON صحيح فقط.
            لا تستخدم Markdown.
            لا تضف أي شرح خارج JSON.

            القواعد العامة:
            - أمامك قائمة من 10 كتب حقيقية ومناسبة من Google Books.
            - اختر كتاباً واحداً فقط من القائمة.
            - لا تخترع كتاباً خارج القائمة.
            - لا تخترع مؤلفاً أو عدد صفحات.
            - لا تختَر كتاباً مكرراً من التحديات الحالية.
            - إذا كانت القائمة تحتوي كتباً متشابهة، اختر الكتاب الأكثر وضوحاً في الوصف وعدد الصفحات.
            - التحدي يجب أن يكون قراءة الكتاب كاملاً.
            - title و description باللغة العربية.

            قواعد الأسئلة:
            - أنشئ 5 أسئلة اختيار من متعدد فقط.
            - كل سؤال يحتوي 4 خيارات: optionA, optionB, optionC, optionD.
            - correctAnswer يجب أن يكون فقط A أو B أو C أو D.
            - الأسئلة يجب أن تختبر فهم القارئ لموضوع الكتاب، هدفه، فكرته الأساسية، والمفاهيم الموجودة في وصف الكتاب.
            - اجعل الأسئلة عميقة نسبيًا، وليست أسئلة سطحية يمكن تخمينها من عنوان الكتاب فقط.
            - لا تجعل الإجابة واضحة من صياغة السؤال.
            - لا تسأل أسئلة عامة جدًا مثل: "ما نوع هذا الكتاب؟" أو "ما موضوع الكتاب؟".
            - لا تسأل عن تفاصيل دقيقة غير موجودة في بيانات الكتاب مثل رقم صفحة، نهاية فصل، شخصية ظهرت في فصل معين، أو اقتباس محدد.
            - كل سؤال يجب أن يكون مرتبطًا بفكرة من وصف الكتاب، لكن بصياغة تحتاج فهمًا وليس نسخًا مباشرًا.
            - اجعل السؤال كأنه يتحقق أن المستخدم قرأ الكتاب أو فهم فكرته، وليس فقط شاهد بياناته.
            """;

        String input = """
            الصعوبة: %s
            الفترة: %s

            عناوين أو أهداف مستخدمة مسبقاً ويجب تجنبها:
            %s

            قائمة الكتب الحقيقية المناسبة من Google Books:
            %s

            اختر كتاباً واحداً فقط من القائمة، ثم أرجع JSON فقط بهذا الشكل بالضبط:

            {
              "title": "string",
              "description": "string",
              "targetName": "string",
              "author": "string",
              "targetValue": 150,
              "verificationSource": "GOOGLE_BOOKS",
              "verificationRule": "BOOK_QUESTIONS",
              "verificationTarget": "string",
              "questions": [
                {
                  "question": "string",
                  "optionA": "string",
                  "optionB": "string",
                  "optionC": "string",
                  "optionD": "string",
                  "correctAnswer": "A"
                },
                {
                  "question": "string",
                  "optionA": "string",
                  "optionB": "string",
                  "optionC": "string",
                  "optionD": "string",
                  "correctAnswer": "B"
                },
                {
                  "question": "string",
                  "optionA": "string",
                  "optionB": "string",
                  "optionC": "string",
                  "optionD": "string",
                  "correctAnswer": "C"
                },
                {
                  "question": "string",
                  "optionA": "string",
                  "optionB": "string",
                  "optionC": "string",
                  "optionD": "string",
                  "correctAnswer": "D"
                },
                {
                  "question": "string",
                  "optionA": "string",
                  "optionB": "string",
                  "optionC": "string",
                  "optionD": "string",
                  "correctAnswer": "A"
                }
              ]
            }

            شروط مهمة جداً:
            - targetName يجب أن يساوي عنوان واحد من قائمة الكتب بالضبط.
            - author يجب أن يكون من نفس بيانات الكتاب المختار.
            - targetValue يجب أن يساوي pageCount للكتاب المختار.
            - verificationTarget يجب أن يساوي targetName.
            - verificationSource يجب أن يكون GOOGLE_BOOKS.
            - verificationRule يجب أن يكون BOOK_QUESTIONS.

            شروط جودة الأسئلة:
            - لا تجعل أي سؤال يمكن الإجابة عليه بمجرد معرفة العنوان أو المؤلف.
            - لا تجعل أي سؤال يسأل عن معلومات metadata مثل عدد الصفحات أو اسم الكاتب.
            - اجعل الإجابة الصحيحة مبنية على الفكرة أو الرسالة أو المفهوم المركزي للكتاب.
            - إذا لم تكن بيانات الكتاب كافية لتوليد أسئلة فهم جيدة، اختر كتاباً آخر من القائمة وصفه أوضح.
            """.formatted(
                difficulty,
                period,
                existingChallenges,
                formatBooksForAi(books)
        );

        return openAiClient.generate(instructions, input);
    }

    private String formatBooksForAi(List<GoogleBookDTO> books) {

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < books.size() && i < 5; i++) {

            GoogleBookDTO book = books.get(i);

            builder.append(i + 1).append(".\n");
            builder.append("title: ").append(book.getTitle()).append("\n");
            builder.append("authors: ").append(book.getAuthors()).append("\n");
            builder.append("pageCount: ").append(book.getPageCount()).append("\n");
            builder.append("description: ").append(shorten(book.getDescription(), 300)).append("\n\n");
        }

        return builder.toString();
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

    private String buildSearchQuery(Difficulty difficulty, Period period) {

        return switch (period) {

            case DAILY -> switch (difficulty) {
                case EASY -> "short children book";
                case MEDIUM -> "short self improvement book";
                case HARD -> "short philosophy book";
            };

            case WEEKLY -> switch (difficulty) {
                case EASY -> "easy short novel";
                case MEDIUM -> "self improvement book";
                case HARD -> "classic literature short book";
            };

            case MONTHLY -> switch (difficulty) {
                case EASY -> "popular nonfiction book";
                case MEDIUM -> "personal development book";
                case HARD -> "classic literature philosophy book";
            };

            default -> "popular book";
        };
    }
}
