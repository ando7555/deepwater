package com.deepwater.amber;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Stops objectionable words in what Amber members write for others to read: profile text and chat messages.
 * The word list lives in {@code amber/blocked-words.txt} so it can be extended without code changes.
 *
 * <p>This is a first line of defence, not moderation: it catches the obvious words, and reports
 * (reviewed in the reports screen) catch the rest.
 */
public final class AmberContentFilter {
    static final String MESSAGE = "Amber does not allow that wording. Please rephrase it.";
    private final Set<String> exact = new HashSet<>();
    private final List<String> prefixes = new ArrayList<>();

    public AmberContentFilter(List<String> entries) {
        for (String raw : entries) {
            String entry = raw.strip().toLowerCase(Locale.ROOT);
            if (entry.isEmpty() || entry.startsWith("#")) continue;
            if (entry.endsWith("*")) prefixes.add(entry.substring(0, entry.length() - 1));
            else exact.add(entry);
        }
    }

    public static AmberContentFilter fromClasspath() {
        try (InputStream in = AmberContentFilter.class.getResourceAsStream("/amber/blocked-words.txt")) {
            if (in == null) throw new IllegalStateException("amber/blocked-words.txt is missing");
            return new AmberContentFilter(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().toList());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** True when any word of the text is on the list. Null and blank text are allowed. */
    public boolean blocked(String text) {
        if (text == null || text.isBlank()) return false;
        for (String word : normalise(text).split("[^\\p{L}]+")) {
            if (word.isEmpty()) continue;
            if (exact.contains(word)) return true;
            for (String prefix : prefixes) if (word.startsWith(prefix)) return true;
        }
        return false;
    }

    /** Lower case, accents kept for Danish and Polish letters, and look-alike digits and symbols turned into letters. */
    static String normalise(String text) {
        String lower = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (char c : lower.toCharArray()) out.append(switch (c) {
            case '0' -> 'o'; case '1', '!' -> 'i'; case '3' -> 'e'; case '4', '@' -> 'a'; case '5', '$' -> 's'; case '7' -> 't';
            default -> c;
        });
        return out.toString();
    }
}
