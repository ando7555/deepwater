package com.deepwater.harbor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Turns a CV into a first draft of a talent profile with fixed rules: a skill list, a headline,
 * years of experience, languages and a short summary. There is no AI here. The draft is shown to
 * the person, who corrects it before it is used for matching.
 */
public final class CvExtractor {
    static final int MAX_TEXT = 60_000;

    private static final List<String> LANGUAGES = List.of("English", "Danish", "Swedish", "Norwegian", "German", "French",
            "Spanish", "Italian", "Portuguese", "Dutch", "Finnish", "Icelandic", "Polish", "Russian", "Ukrainian", "Armenian",
            "Georgian", "Turkish", "Arabic", "Persian", "Hindi", "Urdu", "Chinese", "Mandarin", "Japanese", "Korean", "Greek",
            "Romanian", "Hungarian", "Czech");
    private static final Pattern ROLE_WORDS = Pattern.compile(
            "\\b(engineer|developer|architect|programmer|manager|designer|analyst|consultant|scientist|lead|specialist|"
                    + "administrator|owner|director|officer|tester|devops|sre|accountant|nurse|teacher|marketer)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern YEARS = Pattern.compile("(\\d{1,2})\\s*\\+?\\s*(?:years|yrs)", Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR_RANGE = Pattern.compile(
            "((?:19|20)\\d{2})\\s*(?:-|–|—|to)\\s*((?:19|20)\\d{2}|present|now|today|current)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUMMARY_HEADING = Pattern.compile(
            "^(summary|profile|professional summary|about me|about|objective)\\s*:?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTACT = Pattern.compile("@|https?://|www\\.|\\+?\\d[\\d ()-]{6,}");

    private CvExtractor() {}

    /** Reads the text of a PDF, plain-text or Markdown CV. Word files are not supported yet. */
    public static String readText(String fileName, byte[] bytes) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String text;
        if (name.endsWith(".pdf") || startsWithPdfMarker(bytes)) {
            try (var document = Loader.loadPDF(bytes)) {
                text = new PDFTextStripper().getText(document);
            } catch (IOException ex) {
                throw new IllegalArgumentException("This PDF could not be read. Try saving it again as PDF, or paste the text.");
            }
        } else if (name.endsWith(".txt") || name.endsWith(".md") || name.isEmpty()) {
            text = new String(bytes, StandardCharsets.UTF_8);
        } else {
            throw new IllegalArgumentException("Upload a PDF or .txt file, or paste the text of your CV.");
        }
        text = text.replace("\u0000", "").strip();
        if (text.isEmpty())
            throw new IllegalArgumentException("No text was found in this file. A scanned image CV needs to be pasted as text.");
        return text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text;
    }

    private static boolean startsWithPdfMarker(byte[] bytes) {
        return bytes.length > 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }

    public static Draft extract(String text) {
        return new Draft(headline(text), summary(text), SkillCatalog.find(text), years(text), languages(text));
    }

    /** The first short line near the top that names a role, such as "Senior Backend Engineer". */
    static String headline(String text) {
        String[] lines = text.split("\\R");
        for (int i = 0; i < Math.min(lines.length, 15); i++) {
            String line = lines[i].strip();
            if (line.isEmpty() || line.length() > 90 || CONTACT.matcher(line).find()) continue;
            if (ROLE_WORDS.matcher(line).find()) return line.replaceAll("[|•·]+", " ").replaceAll("\\s+", " ").strip();
        }
        return null;
    }

    /** The paragraph under a "Summary" or "Profile" heading, cut to 400 characters. */
    static String summary(String text) {
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            if (!SUMMARY_HEADING.matcher(lines[i].strip()).matches()) continue;
            StringBuilder paragraph = new StringBuilder();
            for (int j = i + 1; j < lines.length; j++) {
                String line = lines[j].strip();
                if (line.isEmpty()) { if (!paragraph.isEmpty()) break; else continue; }
                if (paragraph.length() > 0 && line.length() < 40 && line.equals(line.toUpperCase(Locale.ROOT))) break;
                paragraph.append(paragraph.isEmpty() ? "" : " ").append(line);
                if (paragraph.length() >= 400) break;
            }
            String result = paragraph.toString().strip();
            if (!result.isEmpty()) return result.length() > 400 ? result.substring(0, 397) + "…" : result;
        }
        return null;
    }

    /**
     * Years of experience: the largest "N years" the CV states, otherwise the span from the earliest
     * year in a date range ("2015 – 2021", "2019 – present") to now.
     */
    static Integer years(String text) {
        int stated = 0;
        Matcher matcher = YEARS.matcher(text);
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            if (value <= 50) stated = Math.max(stated, value);
        }
        if (stated > 0) return stated;
        int now = Year.now().getValue();
        int earliest = Integer.MAX_VALUE;
        matcher = YEAR_RANGE.matcher(text);
        while (matcher.find()) {
            int start = Integer.parseInt(matcher.group(1));
            if (start >= 1970 && start <= now) earliest = Math.min(earliest, start);
        }
        return earliest == Integer.MAX_VALUE ? null : now - earliest;
    }

    static List<String> languages(String text) {
        List<String> found = new ArrayList<>();
        for (String language : LANGUAGES)
            if (Pattern.compile("\\b" + language + "\\b").matcher(text).find()) found.add(language);
        return found;
    }

    public record Draft(String headline, String summary, List<String> skills, Integer yearsExperience, List<String> languages) {}
}
