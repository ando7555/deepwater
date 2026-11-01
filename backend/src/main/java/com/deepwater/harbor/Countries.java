package com.deepwater.harbor;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Country names and a few large cities, used to place postings whose location is free text. */
final class Countries {
    static final Map<String, String> NAMES = new LinkedHashMap<>();
    private static final Map<String, String> CITIES = new LinkedHashMap<>();

    static {
        String[][] names = {{"DK", "Denmark"}, {"SE", "Sweden"}, {"NO", "Norway"}, {"CH", "Switzerland"}, {"FI", "Finland"},
                {"IS", "Iceland"}, {"DE", "Germany"}, {"NL", "Netherlands"}, {"BE", "Belgium"}, {"AT", "Austria"},
                {"FR", "France"}, {"IE", "Ireland"}, {"ES", "Spain"}, {"PT", "Portugal"}, {"IT", "Italy"}, {"PL", "Poland"},
                {"CZ", "Czechia"}, {"EE", "Estonia"}, {"LU", "Luxembourg"}, {"GB", "United Kingdom"}, {"US", "United States"},
                {"CA", "Canada"}, {"AU", "Australia"}, {"AE", "United Arab Emirates"}, {"SG", "Singapore"}, {"AM", "Armenia"}};
        for (String[] pair : names) NAMES.put(pair[0], pair[1]);
        String[][] cities = {{"copenhagen", "DK"}, {"københavn", "DK"}, {"aarhus", "DK"}, {"odense", "DK"}, {"aalborg", "DK"},
                {"stockholm", "SE"}, {"gothenburg", "SE"}, {"göteborg", "SE"}, {"malmö", "SE"}, {"malmo", "SE"},
                {"oslo", "NO"}, {"bergen", "NO"}, {"trondheim", "NO"}, {"zurich", "CH"}, {"zürich", "CH"}, {"geneva", "CH"},
                {"basel", "CH"}, {"bern", "CH"}, {"lausanne", "CH"}, {"helsinki", "FI"}, {"berlin", "DE"}, {"munich", "DE"},
                {"münchen", "DE"}, {"hamburg", "DE"}, {"frankfurt", "DE"}, {"cologne", "DE"}, {"köln", "DE"}, {"stuttgart", "DE"},
                {"düsseldorf", "DE"}, {"leipzig", "DE"}, {"amsterdam", "NL"}, {"rotterdam", "NL"}, {"vienna", "AT"}, {"wien", "AT"},
                {"paris", "FR"}, {"dublin", "IE"}, {"madrid", "ES"}, {"barcelona", "ES"}, {"lisbon", "PT"}, {"warsaw", "PL"},
                {"prague", "CZ"}, {"tallinn", "EE"}, {"london", "GB"}, {"yerevan", "AM"}};
        for (String[] pair : cities) CITIES.put(pair[0], pair[1]);
    }

    private Countries() {}

    /** The country a free-text location names, or null when it cannot be told. */
    static String fromLocation(String location) {
        if (location == null || location.isBlank()) return null;
        String lower = location.toLowerCase(Locale.ROOT);
        for (var entry : NAMES.entrySet())
            if (lower.contains(entry.getValue().toLowerCase(Locale.ROOT))) return entry.getKey();
        if (lower.contains("deutschland")) return "DE";
        if (lower.contains("schweiz") || lower.contains("suisse")) return "CH";
        for (var entry : CITIES.entrySet()) if (lower.contains(entry.getKey())) return entry.getValue();
        return null;
    }

    static String name(String code) { return code == null ? null : NAMES.getOrDefault(code, code); }
}
