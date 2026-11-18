package com.deepwater.harbor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The hiring steps a posting itself describes ("Our interview process: 1. Intro call ... 2. Case interview ...").
 * Harbor never guesses: when the posting has no such list, {@link #read} returns null and the screen says
 * the employer has not published it. Plain string scanning rather than large regular expressions keeps it
 * linear on long postings.
 */
public record HiringProcess(List<Step> steps, int interviews) {
    public record Step(String name, String detail, boolean interview) {}

    private static final List<String> PROCESS_FIRST = List.of("interview", "recruitment", "recruiting", "hiring", "selection", "application");
    private static final List<String> PROCESS_SECOND = List.of("process", "journey", "steps", "stages");
    private static final List<String> LEADING_WORDS = List.of("the", "our", "about", "what", "is", "what's", "how", "does");
    private static final List<String> OTHER_HEADINGS = List.of("how we hire", "what to expect", "what happens next", "our process",
            "next steps in the process", "rekrutteringsproces", "ansættelsesproces", "rekryteringsprocess", "ansettelsesprosess",
            "rekrutteringsprosess", "bewerbungsprozess", "der bewerbungsprozess", "unser bewerbungsprozess", "auswahlprozess",
            "der auswahlprozess", "so geht's weiter", "so gehts weiter");
    private static final List<String> PROSE_TRIGGERS = List.of("includes", "include", "consists of", "consist of", "involves", "involve",
            "looks like this", "is as follows");
    private static final List<String> STEP_WORDS = List.of("interview", "call", "chat", "screen", "test", "assignment", "case",
            "challenge", "assessment", "presentation", "meeting", "conversation", "offer", "reference", "task", "onsite", "on-site",
            "panel", "final", "technical", "culture", "coffee", "fika", "deep dive", "deep-dive", "hiring manager", "recruiter", "talent",
            "home test", "take-home", "pair", "coding", "demo", "pitch", "working session", "samtale", "gespräch", "intervju",
            "kennenlernen", "vorstellung", "opgave", "aufgabe", "referenzen", "tilbud", "angebot", "erbjudande");
    private static final List<String> INTERVIEW_WORDS = List.of("interview", "call", "chat", "screen", "meeting", "conversation", "panel",
            "onsite", "on-site", "coffee", "fika", "deep dive", "deep-dive", "pair", "samtale", "gespräch", "intervju", "kennenlernen",
            "vorstellung");
    private static final List<String> NOT_INTERVIEW_WORDS = List.of("offer", "reference", "tilbud", "angebot", "erbjudande", "referenzen");
    private static final List<String> LIST_WORDS = List.of("step", "stage", "round");
    private static final List<String> FILLER_WORDS = List.of("an", "a", "the", "one", "finally");
    private static final String BULLETS = "-•●*·▪◦‣➤✓✔→";
    private static final String NUMBER_SEPARATORS = ".):-–";
    private static final String ARROW = "→";
    private static final int MAX_STEPS = 8;
    private static final int MAX_SKIPPED = 3;

    /** The steps the posting lists for its hiring process, or null when it does not name at least two. */
    public static HiringProcess read(String text) {
        if (text == null || text.isBlank()) return null;
        String clean = text.replace(' ', ' ').replace(' ', ' ');
        List<String> lines = clean.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        for (int i = 0; i < lines.size(); i++) {
            HiringProcess found = underHeading(lines, i);
            if (found != null) return found;
        }
        return fromProse(clean.replace('\n', ' '));
    }

    private static HiringProcess underHeading(List<String> lines, int index) {
        String line = lines.get(index);
        if (line.length() > 120 || !isHeading(line)) return null;
        // "Interview Process: Recruiter Screen → Hiring Manager → ..." on the heading line or the next one.
        int colon = line.indexOf(':');
        HiringProcess chain = arrows(colon >= 0 ? line.substring(colon + 1) : "");
        if (chain == null && index + 1 < lines.size()) chain = arrows(lines.get(index + 1));
        return chain != null ? chain : stepsAfter(lines, index + 1);
    }

    static boolean isHeading(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        for (String heading : OTHER_HEADINGS) if (lower.startsWith(heading)) return true;
        List<String> words = words(lower);
        int at = 0;
        while (at < words.size() && at < 3 && LEADING_WORDS.contains(words.get(at))) at++;
        return at + 1 < words.size() && PROCESS_FIRST.contains(words.get(at)) && PROCESS_SECOND.contains(words.get(at + 1));
    }

    /** "The recruitment process will include an intro call, an interview with the team and a case." */
    private static HiringProcess fromProse(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String first : PROCESS_FIRST) {
            for (int at = lower.indexOf(first + " process"); at >= 0; at = lower.indexOf(first + " process", at + 1)) {
                HiringProcess listed = proseAfter(text, lower, at + first.length() + " process".length());
                if (listed != null) return listed;
            }
        }
        return null;
    }

    private static HiringProcess proseAfter(String text, String lower, int from) {
        int end = lower.indexOf('.', from);
        if (end < 0) end = lower.length();
        String sentence = lower.substring(from, end);
        for (String trigger : PROSE_TRIGGERS) {
            int at = sentence.indexOf(trigger);
            if (at < 0 || at > 40) continue;
            String rest = text.substring(from + at + trigger.length(), end).strip();
            if (rest.startsWith(":")) rest = rest.substring(1);
            if (rest.length() < 10) continue;
            String joined = rest.replace(" and then ", ",").replace(" followed by ", ",").replace(" and ", ",").replace(';', ',');
            HiringProcess listed = fromParts(joined.split(","));
            if (listed != null) return listed;
        }
        return null;
    }

    private static HiringProcess arrows(String line) {
        String joined = line.replace("->", ARROW).replace("⟶", ARROW).replace("➔", ARROW).replace("»", ARROW);
        if (!joined.contains(ARROW)) return null;
        return fromParts(joined.split(ARROW));
    }

    private static HiringProcess fromParts(String[] parts) {
        List<Step> steps = new ArrayList<>();
        for (String part : parts) {
            String name = dropFillers(part.strip());
            if (name.length() >= 3 && name.length() <= 90 && mentions(name, STEP_WORDS) && steps.size() < MAX_STEPS)
                steps.add(named(name, ""));
        }
        return finish(steps);
    }

    private static HiringProcess stepsAfter(List<String> lines, int start) {
        List<Step> steps = new ArrayList<>();
        int skipped = 0;
        int i = start;
        // An intro sentence or two may come before the list; after the list starts, the first other line ends it.
        while (i < lines.size() && steps.size() < MAX_STEPS && skipped <= MAX_SKIPPED) {
            Step step = step(lines.get(i), true);
            if (step != null) steps.add(step);
            else if (steps.isEmpty()) skipped++;
            else skipped = MAX_SKIPPED + 1;
            i++;
        }
        return finish(steps);
    }

    private static HiringProcess finish(List<Step> steps) {
        if (steps.size() < 2) return null;
        int interviews = (int) steps.stream().filter(Step::interview).count();
        return new HiringProcess(List.copyOf(steps), interviews);
    }

    static Step step(String line) { return step(line, false); }

    /** One list line as a step. {@code underHeading}: a short plain line right after a process heading counts too. */
    static Step step(String line, boolean underHeading) {
        String numbered = afterNumber(line);
        String body = numbered != null ? numbered : afterBullet(line);
        boolean listed = body != null;
        if (!listed) body = line;
        body = body.strip();
        int split = labelEnd(body);
        if (split > 0 && mentions(body.substring(0, split), STEP_WORDS)) {
            String name = body.substring(0, split).replace("*", "").replace("_", "").strip();
            String detail = body.substring(split).replaceFirst("^\\s*[:–—-]", "").strip();
            return name.isEmpty() || wordCount(name) > 9 ? null : named(name, detail);
        }
        int limit = listed ? 110 : 90;
        boolean plainStep = (listed || (underHeading && !body.endsWith("."))) && body.length() <= limit && mentions(body, STEP_WORDS);
        if (!plainStep) return null;
        String name = body.replace("*", "").replace("_", "").strip();
        return name.isEmpty() || wordCount(name) > 9 ? null : named(name, "");
    }

    /** "1. Intro call", "Step 2 – Case", "Round 3: Panel" without the number; null when the line is not numbered. */
    private static String afterNumber(String line) {
        String rest = line;
        String lower = rest.toLowerCase(Locale.ROOT);
        for (String word : LIST_WORDS) if (lower.startsWith(word)) { rest = rest.substring(word.length()).stripLeading(); break; }
        boolean worded = rest.length() != line.length();
        int digits = 0;
        while (digits < rest.length() && digits < 2 && Character.isDigit(rest.charAt(digits))) digits++;
        if (digits == 0) return null;
        rest = rest.substring(digits).stripLeading();
        if (!rest.isEmpty() && NUMBER_SEPARATORS.indexOf(rest.charAt(0)) >= 0) rest = rest.substring(1);
        else if (!worded) return null;
        rest = rest.strip();
        return rest.isEmpty() ? null : rest;
    }

    private static String afterBullet(String line) {
        if (line.length() < 2 || BULLETS.indexOf(line.charAt(0)) < 0) return null;
        String rest = line.substring(1).strip();
        return rest.isEmpty() ? null : rest;
    }

    /** Where a "Name: detail" or "Name – detail" label ends, or -1. */
    private static int labelEnd(String body) {
        int best = -1;
        for (String mark : List.of(":", " – ", " — ", " - ")) {
            int at = body.indexOf(mark);
            if (at >= 3 && at <= 70 && (best < 0 || at < best)) best = at;
        }
        return best;
    }

    private static Step named(String name, String detail) {
        String clean = name;
        while (!clean.isEmpty() && ".;:".indexOf(clean.charAt(clean.length() - 1)) >= 0) clean = clean.substring(0, clean.length() - 1);
        clean = clean.strip();
        String shortDetail = detail.length() > 220 ? detail.substring(0, 217).strip() + "…" : detail;
        boolean interview = mentions(clean, INTERVIEW_WORDS) && !mentions(clean, NOT_INTERVIEW_WORDS);
        return new Step(capitalise(clean), shortDetail, interview);
    }

    private static String dropFillers(String part) {
        String rest = part;
        for (String filler : FILLER_WORDS) {
            if (rest.toLowerCase(Locale.ROOT).startsWith(filler + " ") && rest.length() > filler.length() + 1) {
                rest = rest.substring(filler.length() + 1).strip();
                break;
            }
        }
        return rest;
    }

    /** True when any keyword starts a word of the text (so "call" matches "calls", not "recall"). */
    static boolean mentions(String text, List<String> keywords) {
        String padded = " " + normalise(text) + " ";
        for (String keyword : keywords) if (padded.contains(" " + keyword)) return true;
        return false;
    }

    private static String normalise(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toLowerCase(Locale.ROOT).toCharArray()) out.append(Character.isLetterOrDigit(c) || c == '-' || c == '\'' ? c : ' ');
        return out.toString();
    }

    private static List<String> words(String lower) {
        return List.of(normalise(lower).trim().split(" +"));
    }

    private static int wordCount(String text) {
        return text.isBlank() ? 0 : text.trim().split(" +").length;
    }

    private static String capitalise(String value) {
        return value.isEmpty() ? value : value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }
}
