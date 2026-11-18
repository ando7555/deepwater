package com.deepwater.exam;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What the examiners need to know about the language of an exam: its special letters, linking words
 * and common words (for the fixed rubric) and what to look at in grammar (for the AI examiner).
 *
 * @param letterShare the share of words with a special letter that counts as fully native-looking text
 * @param functionWords common words of the language that are not also English words, so English text is not taken for it
 */
record ExamLanguage(String code, String name, Pattern letters, double letterShare, Set<String> linkingWords,
                    Set<String> functionWords, String linkingExamples, String grammarFocus) {

    static final ExamLanguage POLISH = new ExamLanguage("pl", "Polish", Pattern.compile("[ąćęłńóśźż]"), 0.12,
            Set.of("że", "ponieważ", "bo", "ale", "więc", "dlatego", "który", "która", "które", "którym", "której", "kiedy", "jeśli",
                    "jeżeli", "gdy", "chociaż", "choć", "oraz", "również", "natomiast", "żeby", "gdyby", "potem", "najpierw", "następnie",
                    "jednak", "także", "zatem", "przede"),
            Set.of("nie", "się", "jest", "że", "jak", "mam", "jestem", "ale", "mnie", "moja", "mój", "moje", "bardzo", "czy", "też", "lub",
                    "albo", "dla", "już", "tak", "będzie", "który", "która", "które", "oraz", "przez", "mieszkam", "lubię"),
            "bo, ponieważ, dlatego, ale, który, kiedy",
            "cases, verb forms, aspect and agreement");

    static final ExamLanguage DANISH = new ExamLanguage("da", "Danish", Pattern.compile("[æøå]"), 0.08,
            Set.of("fordi", "derfor", "hvis", "når", "som", "selvom", "da", "først", "bagefter", "derefter", "desuden", "også", "mens",
                    "dog", "alligevel", "nemlig", "derudover", "endelig", "samtidig", "eftersom", "hvorimod", "imidlertid", "både",
                    "hverken", "eller", "så", "men"),
            Set.of("ikke", "jeg", "og", "er", "det", "der", "har", "af", "på", "et", "vi", "mig", "min", "mit", "mine", "meget", "også",
                    "fordi", "som", "skal", "være", "eller", "hvis", "når", "hvor", "hvad", "gerne", "godt", "bliver", "kan", "vil", "jo"),
            "fordi, derfor, men, som, når, selvom",
            "word order (inversion after an opening adverb and the place of ikke), verb tenses, noun gender and definite forms, "
                    + "and adjective endings");

    static final ExamLanguage GERMAN = new ExamLanguage("de", "German", Pattern.compile("[äöüß]"), 0.10,
            Set.of("weil", "denn", "deshalb", "darum", "deswegen", "aber", "obwohl", "wenn", "als", "dass", "damit", "trotzdem",
                    "außerdem", "zuerst", "dann", "danach", "schließlich", "sondern", "jedoch", "nachdem", "während", "bevor", "ob",
                    "sowohl", "einerseits", "andererseits"),
            Set.of("nicht", "ich", "und", "ist", "das", "der", "ein", "eine", "mit", "auf", "für", "sich", "auch", "sehr", "mein", "meine",
                    "wir", "habe", "bin", "wird", "dass", "oder", "aber", "nach", "bei", "von", "zur", "gerne", "möchte", "kann", "keine",
                    "schon", "noch", "ihnen", "mich", "mir", "dich"),
            "weil, deshalb, aber, obwohl, wenn, dass",
            "case endings (nominative, accusative, dative), verb position (verb second, and the verb at the end of a "
                    + "subordinate clause), separable verbs, and adjective endings");

    private static final Map<String, ExamLanguage> BY_CODE = Map.of(POLISH.code(), POLISH, DANISH.code(), DANISH, GERMAN.code(), GERMAN);

    /** The language for a code such as "pl" or "da". */
    static ExamLanguage of(String code) {
        ExamLanguage language = BY_CODE.get(code);
        if (language == null) throw new IllegalArgumentException("No exam rules for language " + code);
        return language;
    }
}
