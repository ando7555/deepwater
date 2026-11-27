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
 * interests, the same language being learned in Echo when both chose to show it, one person speaking the
 * language the other is learning) becomes a sentence the person reads next to the suggestion. There is no hidden score: the order is the same overlaps, counted.
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

    /**
     * What one person brings to matching.
     *
     * @param learning        languages this person learns in Echo. For the viewer it is always filled (it is their
     *                        own data, used only for their own view); for anyone else it is empty unless they chose
     *                        to show it.
     * @param learningShared  whether this person switched "show what I learn in Echo" on
     * @param speaks          languages this person speaks well (native or fluent), from the Deepwater profile; empty
     *                        for anyone else unless they chose to show their languages
     */
    public record Facts(Set<Intent> intents, String area, List<String> interests, Set<String> learning, boolean learningShared,
                        Set<String> speaks) {
        /** The first version's shape: one Echo language, shown only when switched on, and no spoken languages. */
        public Facts(Set<Intent> intents, String area, List<String> interests, String learning) {
            this(intents, area, interests, learning == null ? Set.of() : Set.of(learning), learning != null, Set.of());
        }
    }

    /**
     * Why two people are suggested to each other. Empty {@code sharedIntents} means they are not suggested.
     * {@code exchange} lists the language-exchange reasons: one of them speaks what the other is learning.
     */
    public record Match(Set<Intent> sharedIntents, boolean sameArea, List<String> sharedInterests, boolean sameLearning,
                        List<String> exchange, List<String> reasons) {
        public boolean eligible() { return !sharedIntents.isEmpty(); }
    }

    public static Match match(Facts me, Facts them) {
        Set<Intent> intents = EnumSet.noneOf(Intent.class);
        intents.addAll(me.intents());
        intents.retainAll(them.intents());
        boolean sameArea = !key(me.area()).isEmpty() && key(me.area()).equals(key(them.area()));
        List<String> shared = sharedInterests(me.interests(), them.interests());
        // "Both learning" needs both people to have switched it on; the viewer's private learning is not enough.
        List<String> bothLearning = me.learningShared() && them.learningShared() ? overlap(me.learning(), them.learning()) : List.of();
        List<String> exchange = new ArrayList<>();
        for (String language : overlap(me.learning(), them.speaks())) exchange.add("Speaks " + language + ", which you are learning");
        if (them.learningShared())
            for (String language : overlap(them.learning(), me.speaks())) exchange.add("Is learning " + language + ", which you speak");
        String area = sameArea ? me.area().strip() : null;
        List<String> reasons = intents.isEmpty() ? List.of() : reasons(intents, area, shared, bothLearning, exchange);
        return new Match(intents, sameArea, List.copyOf(shared), !bothLearning.isEmpty(), List.copyOf(exchange), List.copyOf(reasons));
    }

    /** Languages in both sets, compared without case, sorted so the sentences are stable. */
    private static List<String> overlap(Set<String> a, Set<String> b) {
        Set<String> keys = new HashSet<>();
        for (String value : b) keys.add(key(value));
        return a.stream().filter(value -> keys.contains(key(value))).sorted().toList();
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

    private static List<String> reasons(Set<Intent> intents, String area, List<String> shared, List<String> learning,
                                        List<String> exchange) {
        List<String> reasons = new ArrayList<>();
        reasons.add("You are both looking for " + String.join(" and ", intents.stream().map(i -> i.words).toList()));
        if (area != null) reasons.add("You both chose " + area);
        List<String> lower = shared.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
        if (lower.size() == 1) reasons.add("1 shared interest: " + lower.getFirst());
        else if (!lower.isEmpty()) reasons.add(lower.size() + " shared interests: " + String.join(", ", lower));
        for (String language : learning) reasons.add("You are both learning " + language);
        reasons.addAll(exchange);
        return reasons;
    }

    /**
     * More overlaps first: same area, then number of shared interests, then a language exchange (one speaks what
     * the other learns), then the same language being learned.
     */
    public static final Comparator<Match> ORDER = Comparator
            .comparing((Match m) -> m.sameArea() ? 0 : 1)
            .thenComparing(m -> -m.sharedInterests().size())
            .thenComparing(m -> -m.exchange().size())
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
