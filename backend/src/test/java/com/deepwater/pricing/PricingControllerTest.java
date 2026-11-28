package com.deepwater.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Only a real https link is offered to learners as a place to pay. */
class PricingControllerTest {
    @Test
    void onlyHttpsLinksCount() {
        assertThat(PricingController.safe("https://buy.stripe.com/test_abc")).isEqualTo("https://buy.stripe.com/test_abc");
        assertThat(PricingController.safe("  https://buy.stripe.com/x  ")).isEqualTo("https://buy.stripe.com/x");
        assertThat(PricingController.safe("http://buy.stripe.com/x")).isNull();
        assertThat(PricingController.safe("javascript:alert(1)")).isNull();
        assertThat(PricingController.safe("not a url")).isNull();
        assertThat(PricingController.safe("")).isNull();
        assertThat(PricingController.safe(null)).isNull();
    }

    @Test
    void aSetLinkIsShownOnItsOfferOnly() {
        var pricing = new PricingController("https://buy.stripe.com/pl", "", "", "https://buy.stripe.com/course-da", "");
        assertThat(pricing.pricing("pl").offers().getFirst().link()).isEqualTo("https://buy.stripe.com/pl");
        var danish = pricing.pricing("DA").offers();
        assertThat(danish.get(0).link()).isNull();
        assertThat(danish.get(1).link()).isEqualTo("https://buy.stripe.com/course-da");
    }
}
