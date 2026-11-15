package com.deepwater.amber;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AmberContentFilterTest {
    private final AmberContentFilter filter = AmberContentFilter.fromClasspath();

    @Test
    void blocksListedWordsWithLookAlikesAndEndings() {
        assertThat(filter.blocked("FUCK")).isTrue();
        assertThat(filter.blocked("f*ck off")).isFalse(); // symbols other than look-alikes are not guessed
        assertThat(filter.blocked("fucking great")).isTrue();
        assertThat(filter.blocked("wh0re")).isTrue();
        assertThat(filter.blocked("ty kurwo")).isTrue();
    }

    @Test
    void allowsOrdinaryTextIncludingWordsThatContainAListedWord() {
        assertThat(filter.blocked(null)).isFalse();
        assertThat(filter.blocked("")).isFalse();
        assertThat(filter.blocked("Coffee in Scunthorpe, then a walk in Dyrehaven?")).isFalse();
        assertThat(filter.blocked("I like cocktails and Dickens")).isFalse();
        assertThat(filter.blocked("Jeg elsker æbler og øl på Østerbro")).isFalse();
    }
}
