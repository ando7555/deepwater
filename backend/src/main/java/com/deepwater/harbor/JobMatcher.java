package com.deepwater.harbor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Scores how well a posting fits a talent profile, from 0 to 100, and says why in plain sentences.
 * The rule is deliberately simple so a person can check it:
 * up to 60 points for skills (each skill from the profile the posting mentions, capped at five),
 * up to 25 for the job title sharing words with the person's headline,
 * and 15 when the posting is in one of the person's target countries (or remote, when they accept remote).
 * Visa questions never change the score; they are filters and labels, so a good match is not hidden.
 */
public final class JobMatcher {
    private static final Set<String> TITLE_STOP_WORDS = Set.of("and", "or", "the", "a", "an", "of", "for", "in", "at", "to",
            "with", "m", "w", "d", "f", "x", "senior", "junior", "mid", "level", "remote", "hybrid", "full", "time", "part");

    private JobMatcher() {}

    public static Match match(TalentProfile profile, Job job) {
        String text = job.title() + "\n" + String.join(" ", job.tags()) + "\n" + job.description();
        List<String> matched = profile.skills().stream().filter(skill -> SkillCatalog.mentions(text, skill)).toList();
        Set<String> mine = profile.skills().stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        List<String> missing = SkillCatalog.find(job.title() + "\n" + String.join(" ", job.tags())).stream()
                .filter(skill -> !mine.contains(skill.toLowerCase(Locale.ROOT))).limit(4).toList();

        int skillTarget = Math.clamp(profile.skills().size(), 1, 5);
        int skillPoints = profile.skills().isEmpty() ? 0 : Math.round(60f * Math.min(matched.size(), skillTarget) / skillTarget);

        List<String> sharedTitleWords = sharedWords(profile.headline(), job.title());
        int titlePoints = sharedTitleWords.isEmpty() ? 0 : Math.min(25, 13 + 6 * (sharedTitleWords.size() - 1));

        boolean inTarget = job.countryCode() != null && profile.targetCountries().contains(job.countryCode());
        boolean remoteOk = job.remote() && !"ONSITE".equals(profile.remotePreference());
        int placePoints = inTarget || remoteOk || profile.targetCountries().isEmpty() ? 15 : 0;

        var permit = WorkPermitRules.need(WorkPermitRules.citizenship(profile.citizenship()), job.countryCode());

        List<String> reasons = new ArrayList<>();
        if (!matched.isEmpty())
            reasons.add("Mentions " + matched.size() + " of your skills: " + String.join(", ", matched.subList(0, Math.min(6, matched.size()))) + ".");
        else if (!profile.skills().isEmpty())
            reasons.add("Mentions none of the skills in your profile.");
        if (!sharedTitleWords.isEmpty())
            reasons.add("Title shares \"" + String.join(" ", sharedTitleWords) + "\" with your headline.");
        if (inTarget) reasons.add("In " + Countries.name(job.countryCode()) + ", one of your target countries.");
        else if (remoteOk) reasons.add("Remote, which you said you accept.");
        if (!missing.isEmpty()) reasons.add("Asks for skills not in your profile: " + String.join(", ", missing) + ".");

        return new Match(Math.min(100, skillPoints + titlePoints + placePoints), matched, missing, reasons, permit.name());
    }

    static List<String> sharedWords(String headline, String title) {
        if (headline == null || headline.isBlank() || title == null) return List.of();
        Set<String> titleWords = words(title);
        return words(headline).stream().filter(titleWords::contains).toList();
    }

    private static Set<String> words(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}+#]+"))
                .filter(word -> word.length() > 1 && !TITLE_STOP_WORDS.contains(word))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    public record Match(int score, List<String> matchedSkills, List<String> missingSkills, List<String> reasons, String workPermit) {}
}
