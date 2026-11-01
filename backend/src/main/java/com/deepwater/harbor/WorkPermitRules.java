package com.deepwater.harbor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A plain summary of whether a person needs a work permit for a country, from their citizenship group.
 * It covers the EU/EEA/Swiss free-movement area, where the answer is well established, and says
 * "check" everywhere else. It is guidance to start from, not legal advice: the official sites decide.
 */
public final class WorkPermitRules {
    /** EU members plus Iceland, Liechtenstein and Norway (EEA). Switzerland is handled separately. */
    static final Set<String> EU_EEA = Set.of("AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE",
            "IT", "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE", "IS", "LI", "NO");

    public enum Citizenship { EU_EEA, SWISS, OTHER, UNKNOWN }

    public enum PermitNeed { NOT_NEEDED, NEEDED, CHECK }

    public record CountryGuide(String code, String name, String nonEuRoute, String officialSite, String officialUrl) {}

    /** The Nordic countries and Switzerland the owner asked about, with the official immigration site for each. */
    public static final Map<String, CountryGuide> GUIDES = guides();

    private static Map<String, CountryGuide> guides() {
        Map<String, CountryGuide> guides = new LinkedHashMap<>();
        guides.put("DK", new CountryGuide("DK", "Denmark",
                "Citizens outside the EU/EEA and Switzerland need a residence and work permit before they start work. "
                        + "Common routes are tied to a job offer, such as the Pay Limit scheme and the Positive Lists.",
                "New to Denmark (SIRI)", "https://www.nyidanmark.dk/en-GB"));
        guides.put("SE", new CountryGuide("SE", "Sweden",
                "Citizens outside the EU/EEA and Switzerland need a work permit, usually applied for before travelling, "
                        + "based on a job offer whose pay and terms meet Swedish requirements.",
                "Swedish Migration Agency", "https://www.migrationsverket.se/English/Private-individuals/Working-in-Sweden.html"));
        guides.put("NO", new CountryGuide("NO", "Norway",
                "Citizens outside the EU/EEA and Switzerland need a residence permit for work, most often as a skilled worker "
                        + "with a job offer.",
                "Norwegian Directorate of Immigration (UDI)", "https://www.udi.no/en/want-to-apply/work-immigration/"));
        guides.put("CH", new CountryGuide("CH", "Switzerland",
                "Citizens outside the EU/EFTA can be hired only in limited numbers, mostly for highly qualified roles, and the "
                        + "employer usually has to show no suitable Swiss or EU/EFTA candidate was found. The employer applies.",
                "State Secretariat for Migration (SEM)", "https://www.sem.admin.ch/sem/en/home/themen/arbeit.html"));
        return java.util.Collections.unmodifiableMap(guides);
    }

    private WorkPermitRules() {}

    public static PermitNeed need(Citizenship citizenship, String countryCode) {
        if (countryCode == null || citizenship == null || citizenship == Citizenship.UNKNOWN) return PermitNeed.CHECK;
        boolean freeMovementArea = EU_EEA.contains(countryCode) || "CH".equals(countryCode);
        if (!freeMovementArea) return PermitNeed.CHECK;
        return citizenship == Citizenship.OTHER ? PermitNeed.NEEDED : PermitNeed.NOT_NEEDED;
    }

    public static Citizenship citizenship(String value) {
        try { return value == null ? Citizenship.UNKNOWN : Citizenship.valueOf(value); }
        catch (IllegalArgumentException ex) { return Citizenship.UNKNOWN; }
    }
}
