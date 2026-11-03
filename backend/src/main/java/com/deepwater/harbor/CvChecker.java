package com.deepwater.harbor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Checks a CV's styling and formatting with fixed rules, so the person gets concrete fixes before they apply.
 * It looks at what recruiters and applicant tracking systems commonly need: readable text, a sensible length,
 * contact details, the usual sections, dated roles, bullet points, measurable results and a calm layout
 * (few fonts, readable sizes). These are widely given CV guidelines, not a measured predictor of interviews.
 * Layout checks (pages, fonts, sizes) run only for PDFs; pasted text has no layout to check.
 */
public final class CvChecker {
    static final String PASS = "PASS";
    static final String WARN = "WARN";
    static final String FAIL = "FAIL";

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]++@[\\w-]++(?:\\.[\\w-]++)++");
    private static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d ()-]{7,}+");
    private static final Pattern PROFILE_LINK = Pattern.compile("linkedin\\.com/|github\\.com/|https?://|www\\.", Pattern.CASE_INSENSITIVE);
    /** Common bullet characters, including the private-use glyph Word writes for Symbol-font bullets. */
    private static final Pattern BULLET = Pattern.compile("^[•▪●◦‣∙○■□➢➤►✓\uF0B7\uF0A7*–-]\\s?\\S");
    private static final Pattern RESULT = Pattern.compile(
            "\\d+\\s?%|[€$£]\\s?\\d|\\b\\d+(?:[.,]\\d+)?\\s?[kmx]\\b|\\b\\d+\\+?\\s(?:users|customers|clients|people|engineers|developers|"
                    + "services|teams|countries|projects|requests|transactions|ms|hours|days)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIRST_PERSON = Pattern.compile("\\b(?:I|me|my)\\b");

    private static final String[][] SECTIONS = {
            {"Experience", "experience|work history|employment|career"},
            {"Education", "education|degree|university|studies"},
            {"Skills", "skills|technologies|tech stack|competencies"},
    };

    private CvChecker() {}

    public static CvCheck check(String text, CvExtractor.CvLayout layout) {
        String[] lines = text.split("\\R");
        int words = text.split("\\s+").length;
        List<CvCheck.Item> items = new ArrayList<>();
        if (layout != null) {
            items.add(item("readable", PASS, "Text can be read by machines",
                    "Applicant tracking systems can read the words in this PDF."));
            items.add(pages(layout.pages()));
        }
        items.add(length(words));
        items.add(EMAIL.matcher(text).find()
                ? item("email", PASS, "Email address", "Found an email address.")
                : item("email", FAIL, "Email address", "Add an email address at the top so recruiters can reach you."));
        items.add(PHONE.matcher(text).find()
                ? item("phone", PASS, "Phone number", "Found a phone number.")
                : item("phone", WARN, "Phone number", "Add a phone number with the country code, for example +45 12 34 56 78."));
        items.add(PROFILE_LINK.matcher(text).find()
                ? item("links", PASS, "Profile link", "Found a LinkedIn, GitHub or portfolio link.")
                : item("links", WARN, "Profile link", "Add a LinkedIn or portfolio link; recruiters often check it."));
        items.add(sections(lines));
        items.add(CvExtractor.headline(text) != null
                ? item("headline", PASS, "Role headline", "The top of the CV says what role you do.")
                : item("headline", WARN, "Role headline", "Put your role under your name, for example \"Senior Backend Engineer\"."));
        items.add(CvExtractor.years(text.replaceAll("(?i)\\d{1,2}\\+?\\s?(?:years|yrs)", "")) != null
                ? item("dates", PASS, "Dated roles", "Roles have start and end years.")
                : item("dates", WARN, "Dated roles", "Give every role a start and end, for example \"2019 – present\"."));
        items.add(bullets(lines));
        items.add(longParagraphs(lines));
        items.add(results(text));
        items.add(firstPerson(text, words));
        if (layout != null) {
            items.add(fonts(layout.fontFamilies()));
            if (layout.smallestBodySize() != null) items.add(fontSize(layout.smallestBodySize()));
        }
        return new CvCheck(score(items), items);
    }

    private static CvCheck.Item pages(int pages) {
        if (pages <= 2) return item("pages", PASS, "Length in pages", pages + (pages == 1 ? " page." : " pages."));
        if (pages == 3) return item("pages", WARN, "Length in pages", "3 pages. Most recruiters expect 1–2; cut older roles to one line each.");
        return item("pages", FAIL, "Length in pages", pages + " pages. Shorten to 2 pages: keep the last 10 years in detail.");
    }

    private static CvCheck.Item length(int words) {
        if (words < 150) return item("words", FAIL, "Amount of content", words + " words. Too little to judge your experience; describe each role.");
        if (words < 250) return item("words", WARN, "Amount of content", words + " words. Add what you achieved in each role.");
        if (words > 1300) return item("words", WARN, "Amount of content", words + " words. Long CVs get skimmed; aim for 400–900.");
        return item("words", PASS, "Amount of content", words + " words.");
    }

    private static CvCheck.Item sections(String[] lines) {
        List<String> missing = new ArrayList<>();
        for (String[] section : SECTIONS) {
            Pattern keyword = Pattern.compile("\\b(?:" + section[1] + ")\\b", Pattern.CASE_INSENSITIVE);
            boolean found = Arrays.stream(lines).map(CvChecker::headingText).anyMatch(line -> line != null && keyword.matcher(line).find());
            if (!found) missing.add(section[0]);
        }
        if (missing.isEmpty()) return item("sections", PASS, "Standard sections", "Has Experience, Education and Skills headings.");
        return item("sections", missing.size() > 1 ? FAIL : WARN, "Standard sections",
                "Add clear headings for: " + String.join(", ", missing) + ". Tracking systems look for them.");
    }

    private static CvCheck.Item bullets(String[] lines) {
        long count = Arrays.stream(lines).map(String::strip).filter(line -> BULLET.matcher(line).find()).count();
        if (count >= 4) return item("bullets", PASS, "Bullet points", count + " bullet points make your roles easy to scan.");
        return item("bullets", WARN, "Bullet points", "Describe each role in 3–5 short bullet points instead of paragraphs.");
    }

    /** Counts runs of prose (consecutive lines that are not bullets or blank) longer than 70 words. */
    private static CvCheck.Item longParagraphs(String[] lines) {
        int longOnes = 0;
        int run = 0;
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || BULLET.matcher(line).find()) {
                if (run > 70) longOnes++;
                run = 0;
            } else {
                run += line.split("\\s+").length;
            }
        }
        if (run > 70) longOnes++;
        if (longOnes == 0) return item("paragraphs", PASS, "Short blocks of text", "No paragraph is longer than 70 words.");
        return item("paragraphs", WARN, "Short blocks of text", longOnes + (longOnes == 1 ? " paragraph is over 70 words. Split it" : " paragraphs are over 70 words. Split them")
                + " into bullet points.");
    }

    private static CvCheck.Item results(String text) {
        long count = RESULT.matcher(text).results().count();
        if (count >= 2) return item("results", PASS, "Measurable results", count + " numbers show the size of your impact.");
        return item("results", WARN, "Measurable results",
                "Add numbers to your achievements, for example \"cut response time by 40%\" or \"served 2 million users\".");
    }

    private static CvCheck.Item firstPerson(String text, int words) {
        long count = FIRST_PERSON.matcher(text).results().count();
        if (count <= Math.max(5, words / 100)) return item("voice", PASS, "Concise wording", "Written in short phrases.");
        return item("voice", WARN, "Concise wording", "\"I\", \"me\" or \"my\" appears " + count
                + " times. Start lines with a verb instead: \"Built…\", \"Led…\".");
    }

    private static CvCheck.Item fonts(List<String> families) {
        int count = families.size();
        String names = String.join(", ", families.subList(0, Math.min(5, count)));
        if (count <= 2) return item("fonts", PASS, "Fonts", count + (count == 1 ? " font family" : " font families") + (count > 0 ? ": " + names + "." : "."));
        if (count == 3) return item("fonts", WARN, "Fonts", "3 font families (" + names + "). Use one or two for a calmer look.");
        return item("fonts", FAIL, "Fonts", count + " font families (" + names + "). Use one or two fonts throughout.");
    }

    private static CvCheck.Item fontSize(float size) {
        String shown = java.math.BigDecimal.valueOf(size).stripTrailingZeros().toPlainString() + " pt";
        if (size >= 9.5f) return item("fontsize", PASS, "Text size", "Body text is at least " + shown + ".");
        return item("fontsize", WARN, "Text size", "Some body text is " + shown + ". Use 10–12 pt so it reads well on screen and paper.");
    }

    /** PASS counts fully, WARN half, FAIL nothing. */
    static int score(List<CvCheck.Item> items) {
        if (items.isEmpty()) return 0;
        double points = items.stream().mapToDouble(i -> switch (i.status()) {
            case PASS -> 1.0;
            case WARN -> 0.5;
            default -> 0.0;
        }).sum();
        return (int) Math.round(100 * points / items.size());
    }

    private static CvCheck.Item item(String id, String status, String title, String detail) {
        return new CvCheck.Item(id, status, title, detail);
    }

    /** A line that looks like a heading (short, no digits, at most four words), without a trailing colon; otherwise null. */
    static String headingText(String line) {
        String clean = line.strip();
        if (clean.endsWith(":")) clean = clean.substring(0, clean.length() - 1).strip();
        boolean shortLine = !clean.isEmpty() && clean.length() <= 40 && clean.split("\\s+").length <= 4;
        return shortLine && clean.chars().noneMatch(Character::isDigit) ? clean.toLowerCase(Locale.ROOT) : null;
    }
}
