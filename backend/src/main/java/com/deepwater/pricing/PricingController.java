package com.deepwater.pricing;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The paid offers and where to buy them. Prices are the owner's (confirmed 2026-11-25): each offer costs a
 * launch price per month for the first 50 buyers per language, who keep it, and later the full price.
 *
 * <p>Payment happens on the payment provider's own page (a Stripe Payment Link), so this server never sees
 * card details and holds no payment keys. A link is a plain public URL set per environment
 * (PAYMENT_LINK_EXAM_PL, PAYMENT_LINK_COURSE_DA, ...). Without a link the offer shows as "opening soon".
 * The "first 50" limit is set on the link itself, at the provider.
 */
@RestController
@RequestMapping("/api/v1/pricing")
public class PricingController {
    public static final int LAUNCH_BUYERS = 50;
    private final Map<String, String> links;

    public PricingController(@Value("${deepwater.pricing.link.exam-pl:}") String examPl,
                             @Value("${deepwater.pricing.link.exam-da:}") String examDa,
                             @Value("${deepwater.pricing.link.exam-de:}") String examDe,
                             @Value("${deepwater.pricing.link.course-da:}") String courseDa,
                             @Value("${deepwater.pricing.link.course-de:}") String courseDe) {
        // A missing link is null, which Map.of does not allow.
        links = new HashMap<>();
        links.put("exam-pl", safe(examPl));
        links.put("exam-da", safe(examDa));
        links.put("exam-de", safe(examDe));
        links.put("course-da", safe(courseDa));
        links.put("course-de", safe(courseDe));
    }

    /** The offers for one Echo language (pl, da or de); an unknown language has none. */
    @GetMapping
    Pricing pricing(@RequestParam(defaultValue = "pl") String language) {
        String code = language.toLowerCase(Locale.ROOT);
        Price price = PRICES.get(code);
        if (price == null) return new Pricing(code, LAUNCH_BUYERS, List.of());
        List<Offer> offers = OFFERS.stream().filter(o -> o.languages().contains(code))
                .map(o -> new Offer(o.id(), o.name(), o.includes(), price.currency(), price.launch(), price.full(),
                        links.get(o.id() + "-" + code)))
                .toList();
        return new Pricing(code, LAUNCH_BUYERS, offers);
    }

    /** Only an https URL is shown as a buy link; anything else counts as not set. */
    static String safe(String link) {
        if (link == null || link.isBlank()) return null;
        try {
            URI uri = URI.create(link.trim());
            return "https".equals(uri.getScheme()) && uri.getHost() != null ? uri.toString() : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private record Price(String currency, int launch, int full) {}

    private record OfferText(String id, String name, String includes, List<String> languages) {}

    private static final Map<String, Price> PRICES = Map.of(
            "pl", new Price("PLN", 100, 720),
            "da", new Price("DKK", 175, 1249),
            "de", new Price("EUR", 25, 169));

    private static final List<OfferText> OFFERS = List.of(
            new OfferText("exam", "Exam-readiness course",
                    "All practice tests for the exam, marking of every part, AI feedback on writing and speaking, and your readiness after each test.",
                    List.of("pl", "da", "de")),
            new OfferText("course", "Language course",
                    "The Echo course with lessons, phrase cards and daily review, with AI feedback on what you write and say.",
                    List.of("da", "de")));

    /** launchBuyers: how many buyers per language get the launch price and keep it. */
    public record Pricing(String language, int launchBuyers, List<Offer> offers) {}

    /** link is null while payments for this offer are not open yet. */
    public record Offer(String id, String name, String includes, String currency, int launchPrice, int fullPrice, String link) {}
}
