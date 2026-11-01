package com.deepwater.harbor;

import java.time.Instant;
import java.util.List;

/**
 * What Harbor knows about a person's working life: built from their CV, then corrected by them.
 * {@code citizenship} is a group (EU_EEA, SWISS, OTHER or UNKNOWN), never a nationality, because the
 * group is all the work-permit rules need. {@code remotePreference} is ANY, REMOTE_ONLY or ONSITE.
 */
public record TalentProfile(String headline, String summary, List<String> skills, Integer yearsExperience,
                            List<String> languages, String citizenship, List<String> targetCountries,
                            String remotePreference, String cvFileName, boolean hasCv, Instant updatedAt) {
    static TalentProfile empty() {
        return new TalentProfile(null, null, List.of(), null, List.of(), "UNKNOWN", List.of(), "ANY", null, false, null);
    }
}
