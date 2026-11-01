package com.deepwater.harbor;

import java.util.List;
import java.util.regex.Pattern;

/** Reads what a posting's own words say about visa sponsorship. Saying nothing means UNKNOWN, not "no". */
final class VisaSignals {
    private static final List<Pattern> NOT_OFFERED = patterns(
            "no visa sponsorship",
            "not (?:able to |in a position to )?(?:offer |provide )?(?:visa )?sponsor",
            "(?:unable to|cannot|can't|can not) sponsor",
            "without (?:visa )?sponsorship",
            "must (?:already )?(?:have|hold) (?:a |the )?(?:valid )?(?:right|permit) to work",
            "eu work permit required",
            "(?:valid|existing) (?:eu )?work permit");
    private static final List<Pattern> OFFERED = patterns(
            "visa sponsorship",
            "recogni[sz]ed sponsor",
            "sponsor (?:your |a |the )?(?:work )?visa",
            "we sponsor",
            "visa support",
            "relocation (?:and|&) visa",
            "help with (?:your )?(?:visa|work permit)",
            "work permit support",
            "support (?:with |for )?(?:your )?work permit");

    private VisaSignals() {}

    static String read(String text, Boolean sourceFlag) {
        if (mentions(text, NOT_OFFERED)) return "NOT_OFFERED";
        if (Boolean.TRUE.equals(sourceFlag) || mentions(text, OFFERED)) return "OFFERED";
        return "UNKNOWN";
    }

    private static boolean mentions(String text, List<Pattern> patterns) {
        return text != null && patterns.stream().anyMatch(pattern -> pattern.matcher(text).find());
    }

    private static List<Pattern> patterns(String... expressions) {
        return java.util.Arrays.stream(expressions).map(e -> Pattern.compile(e, Pattern.CASE_INSENSITIVE)).toList();
    }
}
