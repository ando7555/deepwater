package com.deepwater.amber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deepwater.amber.AmberRules.Facts;
import com.deepwater.amber.AmberRules.Intent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AmberRulesTest {
    private static final Facts ANNA = new Facts(Set.of(Intent.DATING), "Copenhagen", List.of("Hiking", "Jazz", "Cooking"), null);

    @Test
    void theSuggestionListsEveryReasonInPlainWords() {
        var mia = new Facts(Set.of(Intent.DATING, Intent.FRIENDS), " copenhagen ", List.of("jazz", "Photography", "hiking"), null);
        var match = AmberRules.match(ANNA, mia);
        assertThat(match.eligible()).isTrue();
        assertThat(match.reasons()).containsExactly("You are both looking for dating", "You both chose Copenhagen",
                "2 shared interests: hiking, jazz");
    }

    @Test
    void noSharedIntentMeansNoSuggestionWhateverElseOverlaps() {
        var friendsOnly = new Facts(Set.of(Intent.FRIENDS), "Copenhagen", List.of("Hiking", "Jazz", "Cooking"), null);
        var match = AmberRules.match(ANNA, friendsOnly);
        assertThat(match.eligible()).isFalse();
        assertThat(match.reasons()).isEmpty();
    }

    @Test
    void echoLearningCountsOnlyWhenBothChoseToShowIt() {
        var learner = new Facts(Set.of(Intent.DATING), "Aarhus", List.of(), "Danish");
        var silentLearner = new Facts(Set.of(Intent.DATING), "Aarhus", List.of(), null);
        assertThat(AmberRules.match(learner, learner).reasons()).contains("You are both learning Danish");
        assertThat(AmberRules.match(learner, silentLearner).reasons()).noneMatch(r -> r.contains("learning"));
    }

    @Test
    void moreOverlapsComeFirstAreaBeforeInterests() {
        var sameAreaOneInterest = AmberRules.match(ANNA, new Facts(Set.of(Intent.DATING), "Copenhagen", List.of("Jazz"), null));
        var elsewhereThreeInterests = AmberRules.match(ANNA, new Facts(Set.of(Intent.DATING), "Malmö", List.of("Jazz", "Hiking", "Cooking"), null));
        var nothingElse = AmberRules.match(ANNA, new Facts(Set.of(Intent.DATING), "Oslo", List.of(), null));
        var list = new ArrayList<>(List.of(nothingElse, elsewhereThreeInterests, sameAreaOneInterest));
        list.sort(AmberRules.ORDER);
        assertThat(list).containsExactly(sameAreaOneInterest, elsewhereThreeInterests, nothingElse);
    }

    @Test
    void interestsAreTrimmedDeduplicatedAndLimited() {
        assertThat(AmberRules.cleanInterests(List.of(" Board  games ", "board games", "", "Jazz"))).containsExactly("Board games", "Jazz");
        assertThatThrownBy(() -> AmberRules.cleanInterests(List.of("a,b"))).hasMessageContaining("without commas");
        assertThatThrownBy(() -> AmberRules.cleanInterests(java.util.stream.IntStream.range(0, 13).mapToObj(i -> "i" + i).toList()))
                .hasMessageContaining("up to 12");
    }

    @Test
    void speakingWhatTheOtherLearnsIsAReasonButTheirLearningCountsOnlyIfTheyShowIt() {
        // The viewer's own learning always counts for their own view.
        var me = new Facts(Set.of(Intent.FRIENDS), "Aarhus", List.of(), Set.of("Danish"), false, Set.of("Polish"));
        var danishSpeaker = new Facts(Set.of(Intent.FRIENDS), "Oslo", List.of(), Set.of(), false, Set.of("danish"));
        var shyPolishLearner = new Facts(Set.of(Intent.FRIENDS), "Oslo", List.of(), Set.of(), false, Set.of());
        var openPolishLearner = new Facts(Set.of(Intent.FRIENDS), "Oslo", List.of(), Set.of("Polish"), true, Set.of());

        assertThat(AmberRules.match(me, danishSpeaker).reasons()).contains("Speaks Danish, which you are learning");
        assertThat(AmberRules.match(me, shyPolishLearner).exchange()).isEmpty();
        assertThat(AmberRules.match(me, openPolishLearner).reasons()).contains("Is learning Polish, which you speak");
        // "Both learning" still needs both to show it, even when the viewer learns the same language privately.
        var alsoDanish = new Facts(Set.of(Intent.FRIENDS), "Oslo", List.of(), Set.of("Danish"), true, Set.of());
        assertThat(AmberRules.match(me, alsoDanish).reasons()).noneMatch(r -> r.startsWith("You are both learning"));
        // A language exchange ranks above nothing else in common.
        assertThat(AmberRules.ORDER.compare(AmberRules.match(me, danishSpeaker), AmberRules.match(me, shyPolishLearner))).isNegative();
    }
}
