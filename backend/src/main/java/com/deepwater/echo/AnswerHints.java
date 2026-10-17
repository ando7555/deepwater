package com.deepwater.echo;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;

/**
 * Rule-based hints for typed answers that are close to an accepted answer.
 * A hint never reveals the answer and never turns an incorrect answer into a correct one;
 * learners see it as a tentative suggestion.
 */
final class AnswerHints {
    static final String SPECIAL_LETTERS = "Almost. Check the special letters, such as æ, ø, å or ä, ö, ü, ß.";
    static final String WORD_ORDER = "You have the right words. Check the word order.";
    static final String SPELLING = "Very close. Check the spelling of one word.";

    private AnswerHints() {}

    static String nearMiss(String answer, List<String> accepted) {
        String given = EchoService.normalize(answer);
        for (String expected : accepted) {
            String target = EchoService.normalize(expected);
            if (target.isEmpty()) continue;
            if (!given.equals(target) && foldLetters(given).equals(foldLetters(target))) return SPECIAL_LETTERS;
        }
        for (String expected : accepted) {
            String target = EchoService.normalize(expected);
            if (target.contains(" ") && sortedWords(given).equals(sortedWords(target))) return WORD_ORDER;
        }
        for (String expected : accepted) {
            String target = EchoService.normalize(expected);
            if (target.length() >= 4 && editDistanceAtMostOne(given, target)) return SPELLING;
        }
        return null;
    }

    private static String foldLetters(String value) {
        String replaced = value.replace("æ", "ae").replace("ø", "o").replace("ß", "ss");
        return Normalizer.normalize(replaced, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static String sortedWords(String value) {
        String[] words = value.split(" ");
        Arrays.sort(words);
        return String.join(" ", words);
    }

    private static boolean editDistanceAtMostOne(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 1) return false;
        int i = 0, j = 0, edits = 0;
        while (i < a.length() && j < b.length()) {
            if (a.charAt(i) == b.charAt(j)) { i++; j++; continue; }
            if (++edits > 1) return false;
            if (a.length() > b.length()) i++;
            else if (a.length() < b.length()) j++;
            else { i++; j++; }
        }
        return edits + (a.length() - i) + (b.length() - j) <= 1;
    }
}
