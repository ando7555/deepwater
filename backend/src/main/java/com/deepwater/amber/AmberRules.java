package com.deepwater.amber;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Amber's suggestion rules, in plain Java so they can be read and unit-tested on their own.
 *
 * <p>A suggestion uses only what each person put on their Amber profile. Two people can be suggested to
 * each other only when they share at least one connection intent. Every other overlap (same area, shared
 * interests, the same language being learned in Echo when both chose to show it) becomes a sentence the
 * person reads next to the suggestion. There is no hidden score: the order is the same overlaps, counted.
 */
public final class AmberRules {
    private AmberRules() {}

    public enum Intent {
        DATING("dating"), FRIENDS("new friends");

        final String words;
        Intent(String words) { this.words = words; }
    }

    static final int MAX_INTERESTS = 12;
    static final int MAX_INTEREST_LENGTH = 30;

    /** What one person shares for matching. {@code learning} is null unless they switched the Echo signal on. */
    public record Facts(Set<Intent> intents, String area, List<String> interests, String learning) {}

    /** Why two people are suggested to each other. Empty {@code sharedIntents} means they are not suggested. */
    public record Match(Set<Intent> sharedIntents, boolean sameArea, List<String> sharedInterests, boolean sameLearning,
                        List<String> reasons) {
        public boolean eligible() { return !sharedIntents.isEmpty(); }
    }

    public static Match match(Facts me, Facts them) {
        Set<Intent> intents = EnumSet.noneOf(Intent.class);
        intents.addAll(me.intents());
        intents.retainAll(them.intents());
        boolean sameArea = !key(me.area()).isEmpty() && key(me.area()).equals(key(them.area()));
        List<String> shared = sharedInterests(me.interests(), them.interests());
        boolean sameLearning = me.learning() != null && me.learning().equals(them.learning());
        String area = sameArea ? me.area().strip() : null;
        String learning = sameLearning ? them.learning() : null;
        List<String> reasons = intents.isEmpty() ? List.of() : reasons(intents, area, shared, learning);
        return new Match(intents, sameArea, List.copyOf(shared), sameLearning, List.copyOf(reasons));
    }

    /** The viewer's own interests that the other person also chose, in the viewer's order and spelling. */
    private static List<String> sharedInterests(List<String> mine, List<String> theirs) {
        Set<String> theirKeys = new HashSet<>();
        for (String interest : theirs) theirKeys.add(key(interest));
        Set<String> seen = new HashSet<>();
        List<String> shared = new ArrayList<>();
        for (String interest : mine)
            if (theirKeys.contains(key(interest)) && seen.add(key(interest))) shared.add(interest);
        return shared;
    }

    private static List<String> reasons(Set<Intent> intents, String area, List<String> shared, String learning) {
        List<String> reasons = new ArrayList<>();
        reasons.add("You are both looking for " + String.join(" and ", intents.stream().map(i -> i.words).toList()));
        if (area != null) reasons.add("You both chose " + area);
        List<String> lower = shared.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
        if (lower.size() == 1) reasons.add("1 shared interest: " + lower.getFirst());
        else if (!lower.isEmpty()) reasons.add(lower.size() + " shared interests: " + String.join(", ", lower));
        if (learning != null) reasons.add("You are both learning " + learning);
        return reasons;
    }

    /** More overlaps first: same area, then number of shared interests, then the same language being learned. */
    public static final Comparator<Match> ORDER = Comparator
            .comparing((Match m) -> m.sameArea() ? 0 : 1)
            .thenComparing(m -> -m.sharedInterests().size())
            .thenComparing(m -> m.sameLearning() ? 0 : 1);

    /** Trims, drops blanks and case-insensitive duplicates, and rejects lists that are too long. */
    public static List<String> cleanInterests(List<String> raw) {
        Map<String, String> unique = new LinkedHashMap<>();
        if (raw != null) for (String value : raw) {
            if (value == null || value.isBlank()) continue;
            String clean = value.strip().replaceAll("\\s+", " ");
            if (clean.length() > MAX_INTEREST_LENGTH)
                throw new IllegalArgumentException("Keep each interest under " + MAX_INTEREST_LENGTH + " characters.");
            if (clean.contains(",")) throw new IllegalArgumentException("Add interests one at a time, without commas.");
            unique.putIfAbsent(key(clean), clean);
        }
        if (unique.size() > MAX_INTERESTS) throw new IllegalArgumentException("Choose up to " + MAX_INTERESTS + " interests.");
        return List.copyOf(unique.values());
    }

    private static String key(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
