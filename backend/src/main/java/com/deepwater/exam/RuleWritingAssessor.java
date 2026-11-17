package com.deepwater.exam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A fixed rubric for open answers, used when the AI examiner is off. It cannot judge grammar the way
 * a teacher can, so it only measures what it can count (length, linking words, word variety, the
 * language's own letters, whether the answer is on the topic) and says that its mark is a rough estimate.
 */
public final class RuleWritingAssessor implements WritingAssessor {
    private static final Pattern WORD = Pattern.compile("[\\p{L}]+");

    @Override
    public Review review(Target target, List<OpenAnswer> answers) {
        ExamLanguage language = target.rules();
        Map<String, ItemAssessment> items = new LinkedHashMap<>();
        for (OpenAnswer answer : answers) items.put(answer.itemId(), assess(language, answer));
        return new Review(items, null, null, RULES);
    }

    static ItemAssessment assess(ExamLanguage language, OpenAnswer answer) {
        String text = answer.text() == null ? "" : answer.text().strip();
        List<String> words = WORD.matcher(text.toLowerCase(Locale.ROOT)).results().map(m -> m.group()).toList();
        if (words.isEmpty())
            return new ItemAssessment(0, new Criteria(0, 0, 0, 0), "A1", "No answer was given.", List.of(),
                    List.of(), List.of("Write or say something for every task: an empty answer scores 0."), RULES);
        if (!looksLike(language, words))
            return new ItemAssessment(0, new Criteria(0, 0, 0, 0), "A1", "This does not look like " + language.name() + ", so it was not marked.",
                    List.of(), List.of(), List.of("Answer in " + language.name() + "."), RULES);

        List<String> strengths = new ArrayList<>();
        List<String> tips = new ArrayList<>();
        int task = taskScore(answer, words, strengths, tips);
        int coherence = coherenceScore(language, text, words, strengths, tips);
        int vocabulary = vocabularyScore(words, strengths, tips);
        // Without a teacher or the AI, grammar is only guessed from the language's letters and sentence shape.
        int grammar = grammarGuess(language, text, words);
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

    private static int coherenceScore(ExamLanguage language, String text, List<String> words, List<String> strengths, List<String> tips) {
        long linking = words.stream().filter(language.linkingWords()::contains).distinct().count();
        long sentences = Arrays.stream(text.split("[.!?]+")).filter(s -> !s.isBlank()).count();
        int score = (int) Math.min(15, linking * 3) + (int) Math.min(10, sentences * 2);
        if (linking >= 3) strengths.add("You join ideas with linking words (" + linking + " different ones).");
        else tips.add("Join sentences with words like: " + language.linkingExamples() + ".");
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

    private static int grammarGuess(ExamLanguage language, String text, List<String> words) {
        long withOwnLetters = words.stream().filter(w -> language.letters().matcher(w).find()).count();
        double share = (double) withOwnLetters / words.size();
        int letters = share >= language.letterShare() ? 10 : (int) Math.round(share / language.letterShare() * 10);
        boolean capitalised = Arrays.stream(text.split("[.!?]+\\s*")).filter(s -> !s.isBlank())
                .allMatch(s -> Character.isUpperCase(s.strip().charAt(0)));
        return letters + (capitalised ? 5 : 2);
    }

    private static boolean looksLike(ExamLanguage language, List<String> words) {
        long own = words.stream().filter(w -> language.functionWords().contains(w) || language.letters().matcher(w).find()).count();
        return own * 6 >= words.size();
    }

    static String levelFor(int score) {
        if (score >= 85) return "B2";
        if (score >= 60) return "B1";
        if (score >= 35) return "A2";
        return "A1";
    }
}
