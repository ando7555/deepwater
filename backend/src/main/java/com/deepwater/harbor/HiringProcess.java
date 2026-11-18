package com.deepwater.harbor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The hiring steps a posting itself describes ("Our interview process: 1. Intro call ... 2. Case interview ...").
 * Harbor never guesses: when the posting has no such list, {@link #read} returns null and the screen says
 * the employer has not published it.
 */
public record HiringProcess(List<Step> steps, int interviews) {
    public record Step(String name, String detail, boolean interview) {}

    private static final String PROCESS = "(?:interview|recruitment|recruiting|hiring|selection|application)\\s+(?:process|journey|steps|stages)";
    private static final Pattern HEADING = Pattern.compile(
            "^(?:the |our |about the |what is the |how does the |what's the )?" + PROCESS + "\\b.{0,90}$"
            + "|^(?:how we hire|what to expect|what happens next|our process|next steps in the process)\\b.{0,60}$"
            + "|^(?:rekrutteringsproces(?:sen)?|ansættelsesproces(?:sen)?|rekryteringsprocess(?:en)?|ansettelsesprosess(?:en)?|rekrutteringsprosess(?:en)?|(?:der |unser )?bewerbungsprozess|(?:der |unser )?auswahlprozess|so geht'?s weiter)\\b.{0,60}$",
            Pattern.CASE_INSENSITIVE);
    /** "Our recruitment process will include an intro call, an interview with the team and a case." */
    private static final Pattern PROSE = Pattern.compile(
            "\\b" + PROCESS + "\\b[^.:]{0,40}?\\b(?:will )?(?:typically |usually |normally )?(?:includes?|consists? of|involves?|looks like this|is as follows)\\s*:?\\s*([^.]{10,400})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ARROWS = Pattern.compile("\\s*(?:→|->|⟶|➔|»)\\s*");
    private static final Pattern NUMBERED = Pattern.compile("(?i)^(?:step\\s*)?(\\d{1,2})\\s*[.):\\-–]\\s*(.+)$|^(?:step|stage|round)\\s+\\d{1,2}\\s*[:.\\-–]?\\s*(.+)$");
    private static final Pattern BULLET = Pattern.compile("^[-•●*·▪◦‣➤✓✔→]\\s*(.+)$");
    private static final Pattern LABELLED = Pattern.compile("^([^:–—]{3,70}?)\\s*(?::|\\s[–—-]\\s)\\s*(.*)$");
    private static final Pattern STEP_WORD = Pattern.compile("(?i)interview|call\\b|chat\\b|screen|test\\b|assignment|case\\b|case study|challenge|assessment|presentation|meeting|conversation|offer\\b|reference|task\\b|on-?site|panel|final|technical|culture|coffee|fika|deep[ -]dive|hiring manager|recruiter|talent|home[ -]?(?:test|task|assignment)|pair(?:ing)?\\b|coding|demo|pitch|working session|samtale|gespräch|intervju|kennenlernen|vorstellung|opgave|aufgabe|referenzen|tilbud|angebot|erbjudande");
    private static final Pattern INTERVIEW_WORD = Pattern.compile("(?i)interview|call\\b|chat\\b|screen|meeting|conversation|panel|on-?site|coffee|fika|deep[ -]dive|pair(?:ing)?\\b|samtale|gespräch|intervju|kennenlernen|vorstellung");
    private static final Pattern NOT_INTERVIEW = Pattern.compile("(?i)\\boffer\\b|reference|tilbud|angebot|erbjudande|referenzen");
    private static final int MAX_STEPS = 8;

    /** The steps the posting lists for its hiring process, or null when it does not name at least two. */
    public static HiringProcess read(String text) {
        if (text == null || text.isBlank()) return null;
        text = text.replace('\u00a0', ' ').replace('\u202f', ' ');
        List<String> lines = text.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.length() > 120 || !HEADING.matcher(line).find()) continue;
            // "Interview Process: Recruiter Screen → Hiring Manager → ..." on the heading line or the next one.
            HiringProcess chain = arrows(line.contains(":") ? line.substring(line.indexOf(':') + 1) : "");
            if (chain == null && i + 1 < lines.size()) chain = arrows(lines.get(i + 1));
            if (chain != null) return chain;
            HiringProcess found = stepsAfter(lines, i + 1);
            if (found != null) return found;
        }
        Matcher prose = PROSE.matcher(text.replace('\n', ' '));
        while (prose.find()) {
            HiringProcess listed = fromParts(prose.group(1).split("\\s*(?:,|;|\\band then\\b|\\bfollowed by\\b|\\band\\b)\\s*"));
            if (listed != null) return listed;
        }
        return null;
    }

    private static HiringProcess arrows(String line) {
        if (!ARROWS.matcher(line).find()) return null;
        return fromParts(ARROWS.split(line.strip()));
    }

    private static HiringProcess fromParts(String[] parts) {
        List<Step> steps = new ArrayList<>();
        for (String part : parts) {
            String name = part.strip().replaceAll("^(?:an?|the|one|two|a final|finally,?)\\s+(?=\\S)", "").strip();
            if (name.length() < 3 || name.length() > 90 || !STEP_WORD.matcher(name).find()) continue;
            if (steps.size() < MAX_STEPS) steps.add(named(name, ""));
        }
        return finish(steps);
    }

    private static HiringProcess stepsAfter(List<String> lines, int start) {
        List<Step> steps = new ArrayList<>();
        int skipped = 0;
        for (int i = start; i < lines.size() && steps.size() < MAX_STEPS; i++) {
            Step step = step(lines.get(i), true);
            if (step != null) { steps.add(step); continue; }
            // An intro sentence or two may come before the list; after the list starts, the first other line ends it.
            if (!steps.isEmpty() || ++skipped > 3) break;
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
        String body = line;
        boolean listed = false;
        Matcher numbered = NUMBERED.matcher(body);
        if (numbered.matches()) { body = numbered.group(2) != null ? numbered.group(2) : numbered.group(3); listed = true; }
        else {
            Matcher bullet = BULLET.matcher(body);
            if (bullet.matches()) { body = bullet.group(1); listed = true; }
        }
        body = body.strip();
        String name;
        String detail;
        Matcher labelled = LABELLED.matcher(body);
        if (labelled.matches() && STEP_WORD.matcher(labelled.group(1)).find()) {
            name = labelled.group(1).strip();
            detail = labelled.group(2).strip();
        } else if ((listed || underHeading) && body.length() <= (listed ? 110 : 90) && STEP_WORD.matcher(body).find()
                && (listed || !body.endsWith("."))) {
            name = body;
            detail = "";
        } else {
            return null;
        }
        name = name.replaceAll("[*_]+", "").strip();
        if (name.isEmpty() || name.split("\\s+").length > 9) return null;
        return named(name, detail);
    }

    private static Step named(String name, String detail) {
        name = name.replaceAll("[.;:]+$", "").strip();
        if (detail.length() > 220) detail = detail.substring(0, 217).strip() + "…";
        boolean interview = INTERVIEW_WORD.matcher(name).find() && !NOT_INTERVIEW.matcher(name).find();
        return new Step(capitalise(name), detail, interview);
    }

    private static String capitalise(String value) {
        return value.isEmpty() ? value : value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }
}
