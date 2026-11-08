package com.deepwater.exam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A fixed rubric for open answers, used when the AI examiner is off. It cannot judge grammar the way
 * a teacher can, so it only measures what it can count (length, linking words, word variety, Polish
 * letters, whether the answer is on the topic) and says that its mark is a rough estimate.
 */
public final class RuleWritingAssessor implements WritingAssessor {
    private static final Pattern WORD = Pattern.compile("[\\p{L}]+");
    private static final Pattern POLISH_LETTERS = Pattern.compile("[ąćęłńóśźż]");
    private static final Set<String> LINKING_WORDS = Set.of("że", "ponieważ", "bo", "ale", "więc", "dlatego", "który", "która", "które",
            "którym", "której", "kiedy", "jeśli", "jeżeli", "gdy", "chociaż", "choć", "oraz", "również", "natomiast", "żeby", "gdyby",
            "potem", "najpierw", "następnie", "jednak", "także", "zatem", "przede");
    /** Common Polish words that are not also English words, so "i" and "to" do not count. */
    private static final Set<String> POLISH_FUNCTION_WORDS = Set.of("nie", "się", "jest", "że", "jak", "mam", "jestem", "ale", "mnie",
            "moja", "mój", "moje", "bardzo", "czy", "też", "lub", "albo", "dla", "już", "tak", "będzie", "który", "która", "które", "oraz",
            "przez", "mieszkam", "lubię");

    @Override
    public Review review(List<OpenAnswer> answers) {
        Map<String, ItemAssessment> items = new LinkedHashMap<>();
        for (OpenAnswer answer : answers) items.put(answer.itemId(), assess(answer));
        return new Review(items, null, null, RULES);
    }

    static ItemAssessment assess(OpenAnswer answer) {
        String text = answer.text() == null ? "" : answer.text().strip();
        List<String> words = WORD.matcher(text.toLowerCase(Locale.ROOT)).results().map(m -> m.group()).toList();
        if (words.isEmpty())
            return new ItemAssessment(0, new Criteria(0, 0, 0, 0), "A1", "No answer was given.", List.of(),
                    List.of(), List.of("Write or say something for every task: an empty answer scores 0."), RULES);
        if (!looksPolish(words))
            return new ItemAssessment(0, new Criteria(0, 0, 0, 0), "A1", "This does not look like Polish, so it was not marked.", List.of(),
                    List.of(), List.of("Answer in Polish."), RULES);

        List<String> strengths = new ArrayList<>();
        List<String> tips = new ArrayList<>();
        int task = taskScore(answer, words, strengths, tips);
        int coherence = coherenceScore(text, words, strengths, tips);
        int vocabulary = vocabularyScore(words, strengths, tips);
        // Without a teacher or the AI, grammar is only guessed from the Polish letters and sentence shape.
        int grammar = grammarGuess(text, words);
        tips.add("This mark comes from fixed rules (length, linking words, word variety), not from a teacher or the AI examiner, "
                + "so grammar is only roughly estimated (at most 15 of 25).");
        int score = task + coherence + vocabulary + grammar;
        return new ItemAssessment(score, new Criteria(task, coherence, vocabulary, grammar), levelFor(score),
                "Rough estimate from fixed rules: " + words.size() + " words.", strengths, List.of(), tips, RULES);
    }

    private static int taskScore(OpenAnswer answer, List<String> words, List<String> strengths, List<String> tips) {
        int count = words.size();
        int length;
        if (count < answer.minWords()) {
            length = (int) Math.round(15.0 * count / Math.max(1, answer.minWords()));
            tips.add("Write at least " + answer.minWords() + " words; you wrote " + count + ".");
        } else if (count > answer.maxWords() * 1.3) {
            length = 11;
            tips.add("Keep to about " + answer.maxWords() + " words; you wrote " + count + ".");
        } else {
            length = 15;
            strengths.add("The length fits the task.");
        }
        String joined = String.join(" ", words);
        List<String> topic = answer.topicWords() == null ? List.of() : answer.topicWords();
        long onTopic = topic.stream().filter(t -> joined.contains(t.toLowerCase(Locale.ROOT))).count();
        int topicScore = topic.isEmpty() ? 10 : (int) Math.min(10, Math.round(10.0 * onTopic / Math.min(2, topic.size())));
        if (topicScore < 5) tips.add("Answer every point of the task; the answer seems to miss its topic.");
        return length + topicScore;
    }

    private static int coherenceScore(String text, List<String> words, List<String> strengths, List<String> tips) {
        long linking = words.stream().filter(LINKING_WORDS::contains).distinct().count();
        long sentences = Arrays.stream(text.split("[.!?]+")).filter(s -> !s.isBlank()).count();
        int score = (int) Math.min(15, linking * 3) + (int) Math.min(10, sentences * 2);
        if (linking >= 3) strengths.add("You join ideas with linking words (" + linking + " different ones).");
        else tips.add("Join sentences with words like: bo, ponieważ, dlatego, ale, który, kiedy.");
        return Math.min(25, score);
    }

    private static int vocabularyScore(List<String> words, List<String> strengths, List<String> tips) {
        long distinct = words.stream().distinct().count();
        double variety = (double) distinct / words.size();
        int score = (int) Math.round(Math.clamp((variety - 0.35) / 0.4, 0, 1) * 25);
        if (score >= 18) strengths.add("You use a good range of different words.");
        else tips.add("Try not to repeat the same words; use synonyms.");
        return score;
    }

    private static int grammarGuess(String text, List<String> words) {
        long withPolishLetters = words.stream().filter(w -> POLISH_LETTERS.matcher(w).find()).count();
        double share = (double) withPolishLetters / words.size();
        int letters = share >= 0.12 ? 10 : (int) Math.round(share / 0.12 * 10);
        boolean capitalised = Arrays.stream(text.split("[.!?]+\\s*")).filter(s -> !s.isBlank())
                .allMatch(s -> Character.isUpperCase(s.strip().charAt(0)));
        return letters + (capitalised ? 5 : 2);
    }

    private static boolean looksPolish(List<String> words) {
        long polish = words.stream().filter(w -> POLISH_FUNCTION_WORDS.contains(w) || POLISH_LETTERS.matcher(w).find()).count();
        return polish * 6 >= words.size();
    }

    static String levelFor(int score) {
        if (score >= 85) return "B2";
        if (score >= 60) return "B1";
        if (score >= 35) return "A2";
        return "A1";
    }
}
