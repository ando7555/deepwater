package com.deepwater.harbor;

import java.util.regex.Pattern;

/** Reads what a posting's own words say about visa sponsorship. Saying nothing means UNKNOWN, not "no". */
final class VisaSignals {
    private static final Pattern NOT_OFFERED = Pattern.compile(
            "no visa sponsorship|not (?:able to |in a position to )?(?:offer |provide )?(?:visa )?sponsor|unable to sponsor|cannot sponsor|"
                    + "can't sponsor|can not sponsor|without (?:visa )?sponsorship|must (?:already )?(?:have|hold) (?:a |the )?(?:valid )?(?:right|permit) to work|"
                    + "eu work permit required|(?:valid|existing) (?:eu )?work permit",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OFFERED = Pattern.compile(
            "visa sponsorship|recogni[sz]ed sponsor|sponsor (?:your |a |the )?(?:work )?visa|we sponsor|visa support|relocation and visa|relocation & visa|"
                    + "help with (?:your )?(?:visa|work permit)|work permit support|support (?:with |for )?(?:your )?work permit",
            Pattern.CASE_INSENSITIVE);

    private VisaSignals() {}

    static String read(String text, Boolean sourceFlag) {
        if (text != null && NOT_OFFERED.matcher(text).find()) return "NOT_OFFERED";
        if (Boolean.TRUE.equals(sourceFlag) || (text != null && OFFERED.matcher(text).find())) return "OFFERED";
        return "UNKNOWN";
    }
}
