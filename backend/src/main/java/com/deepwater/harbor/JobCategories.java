package com.deepwater.harbor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Sorts postings into a few job families and reads what a posting says about the local language,
 * with plain rules on the title, tags and description (no AI). Both are shown with every job so a
 * person can see, for example, that Danish is "a plus" here and practise it in Echo.
 */
public final class JobCategories {
    public static final String OTHER = "OTHER";

    /** Job families, in the order they are checked: the first that matches wins. */
    private static final List<Family> FAMILIES = List.of(
            new Family("PRODUCT_LEAD", "Product & leadership",
                    "\\b(product owner|product manager|engineering manager|head of|director|scrum master|team lead|tech lead)\\b"),
            new Family("DATA_AI", "Data & AI",
                    "\\b(data|machine learning|ml|ai|llm|analyst|scientist|analytics)\\b"),
            new Family("CLOUD_DEVOPS", "Cloud & DevOps",
                    "\\b(devops|platform|sre|site reliability|cloud|infrastructure|kubernetes)\\b"),
            new Family("FRONTEND", "Frontend & full-stack",
                    "\\b(frontend|front-end|full-stack|fullstack|react|ui|mobile)\\b"),
            new Family("BACKEND", "Backend",
                    "\\b(backend|back-end|java|kotlin|software engineer|developer|go|python)\\b"));

    private static final String LANGUAGES = "danish|swedish|norwegian|german|french|dutch|finnish|polish|italian|icelandic";
    private static final Pattern LOCAL_LANGUAGE = Pattern.compile("\\b(" + LANGUAGES + ")\\b");
    private static final Pattern NOT_NEEDED = Pattern.compile("\\b(not required|not needed|no need|not necessary|not a must)\\b");
    private static final Pattern A_PLUS = Pattern.compile("\\b(a plus|nice to have|an advantage|a bonus|helpful|lessons|preferred)\\b");
    private static final Pattern REQUIRED = Pattern.compile("\\b(required|must|fluent|fluency|professional level|native|level|[abc][12])\\b");
    private static final Pattern ENGLISH_ONLY = Pattern.compile(
            "english is (our|the) (working|company|office) language|working language is english|english-speaking|in english");

    private JobCategories() {}

    /** The job family of a posting, read from its title first and its tags second. */
    public static String category(Job job) {
        String title = lower(job.title());
        for (Family f : FAMILIES) if (f.pattern().matcher(title).find()) return f.code();
        String tags = lower(String.join(" ", job.tags() == null ? List.of() : job.tags()));
        for (Family f : FAMILIES) if (f.pattern().matcher(tags).find()) return f.code();
        return OTHER;
    }

    /** Ordered labels for the frontend, with OTHER last. */
    public static Map<String, String> orderedLabels() {
        Map<String, String> ordered = new LinkedHashMap<>();
        FAMILIES.forEach(f -> ordered.put(f.code(), f.label()));
        ordered.put(OTHER, "Other roles");
        return ordered;
    }

    /**
     * What the posting says about the local language. need is REQUIRED, PLUS, ENGLISH_OK or NOT_STATED;
     * language is the local language it names (for example "Danish"), or null.
     */
    public static LanguageNeed languageNeed(String description) {
        String text = lower(description);
        LanguageNeed best = new LanguageNeed("NOT_STATED", null);
        if (ENGLISH_ONLY.matcher(text).find()) best = new LanguageNeed("ENGLISH_OK", null);
        for (String clause : text.split("[.;!?\\n]")) {
            var language = LOCAL_LANGUAGE.matcher(clause);
            if (!language.find()) continue;
            String name = capitalise(language.group(1));
            LanguageNeed found = clauseNeed(clause, name);
            if (found != null && found.rank() > best.rank()) best = found;
        }
        return best;
    }

    private static LanguageNeed clauseNeed(String clause, String language) {
        boolean plus = A_PLUS.matcher(clause).find();
        if (NOT_NEEDED.matcher(clause).find()) return new LanguageNeed(plus ? "PLUS" : "ENGLISH_OK", plus ? language : null);
        if (plus) return new LanguageNeed("PLUS", language);
        if (REQUIRED.matcher(clause).find()) return new LanguageNeed("REQUIRED", language);
        return null;
    }

    private static String lower(String text) { return text == null ? "" : text.toLowerCase(Locale.ROOT); }

    private static String capitalise(String word) { return Character.toUpperCase(word.charAt(0)) + word.substring(1); }

    public record LanguageNeed(String need, String language) {
        int rank() {
            return switch (need) {
                case "REQUIRED" -> 3;
                case "PLUS" -> 2;
                case "ENGLISH_OK" -> 1;
                default -> 0;
            };
        }
    }

    private record Family(String code, String label, Pattern pattern) {
        Family(String code, String label, String regex) { this(code, label, Pattern.compile(regex)); }
    }
}
