package com.deepwater.harbor;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Keeps one talent profile per Deepwater account. The CV text is private and deleted with the account. */
@Service
public class TalentProfileService {
    private static final String UNKNOWN = "UNKNOWN";
    private static final Set<String> REMOTE = Set.of("ANY", "REMOTE_ONLY", "ONSITE");
    private final JdbcTemplate db;

    private final tools.jackson.databind.ObjectMapper json;

    public TalentProfileService(JdbcTemplate db, tools.jackson.databind.ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    public TalentProfile profile(String accountId) {
        return db.query("select * from harbor_profiles where account_id=?", (rs, row) -> {
            Timestamp updated = rs.getTimestamp("updated_at");
            return new TalentProfile(rs.getString("headline"), rs.getString("summary"), split(rs.getString("skills")),
                    (Integer) rs.getObject("years_experience"), split(rs.getString("languages")), rs.getString("citizenship"),
                    split(rs.getString("target_countries")), rs.getString("remote_preference"), rs.getString("cv_file_name"),
                    rs.getString("cv_text") != null, updated == null ? null : updated.toInstant(), readCheck(rs.getString("cv_check")));
        }, accountId).stream().findFirst().orElse(TalentProfile.empty());
    }

    /**
     * Reads a CV and fills the profile's headline, summary, skills, years and languages from it.
     * Choices the person made that a CV cannot tell (citizenship, target countries, remote) are kept.
     */
    @Transactional
    public TalentProfile importCv(String accountId, String fileName, CvExtractor.CvDocument document) {
        String text = document.text();
        var draft = CvExtractor.extract(text);
        var current = profile(accountId);
        var next = new TalentProfile(draft.headline(), draft.summary(), draft.skills(), draft.yearsExperience(), draft.languages(),
                current.citizenship(), current.targetCountries(), current.remotePreference(), fileName, true, null);
        save(accountId, next, text);
        db.update("update harbor_profiles set cv_check=? where account_id=?",
                json.writeValueAsString(CvChecker.check(text, document.layout())), accountId);
        return profile(accountId);
    }

    @Transactional
    public TalentProfile update(String accountId, ProfileInput input) {
        var current = profile(accountId);
        String headline = optional(input.headline(), 200, "Headline");
        String summary = optional(input.summary(), 2000, "Summary");
        List<String> skills = SkillCatalog.normalise(input.skills());
        if (skills.size() > 60) throw bad("Keep your profile to 60 skills or fewer.");
        Integer years = input.yearsExperience();
        if (years != null && (years < 0 || years > 60)) throw bad("Years of experience must be between 0 and 60.");
        List<String> languages = clean(input.languages(), 20, 40);
        String citizenship = input.citizenship() == null ? UNKNOWN : input.citizenship();
        if (WorkPermitRules.citizenship(citizenship).name().equals(UNKNOWN) && !UNKNOWN.equals(citizenship))
            throw bad("Choose EU/EEA, Swiss, another citizenship, or prefer not to say.");
        List<String> countries = clean(input.targetCountries(), 30, 2).stream().map(c -> c.toUpperCase(Locale.ROOT)).toList();
        if (countries.stream().anyMatch(c -> !c.matches("[A-Z]{2}"))) throw bad("Use two-letter country codes such as DK or CH.");
        String remote = input.remotePreference() == null ? "ANY" : input.remotePreference();
        if (!REMOTE.contains(remote)) throw bad("Choose any, remote only, or on-site.");
        var next = new TalentProfile(headline, summary, skills, years, languages, citizenship, countries, remote,
                current.cvFileName(), current.hasCv(), null);
        save(accountId, next, null);
        return profile(accountId);
    }

    /** Forgets the CV file and its text but keeps the profile the person corrected. */
    @Transactional
    public TalentProfile deleteCv(String accountId) {
        db.update("update harbor_profiles set cv_file_name=null, cv_text=null, cv_check=null, updated_at=current_timestamp where account_id=?", accountId);
        return profile(accountId);
    }

    private void save(String accountId, TalentProfile p, String cvText) {
        Object[] values = {p.headline(), p.summary(), String.join(",", p.skills()), p.yearsExperience(), String.join(",", p.languages()),
                p.citizenship(), String.join(",", p.targetCountries()), p.remotePreference(), p.cvFileName()};
        String set = "headline=?,summary=?,skills=?,years_experience=?,languages=?,citizenship=?,target_countries=?,remote_preference=?,cv_file_name=?";
        int updated = cvText == null
                ? db.update("update harbor_profiles set " + set + ",updated_at=current_timestamp where account_id=?", append(values, accountId))
                : db.update("update harbor_profiles set " + set + ",cv_text=?,updated_at=current_timestamp where account_id=?", append(append(values, cvText), accountId));
        if (updated == 0)
            db.update("insert into harbor_profiles(headline,summary,skills,years_experience,languages,citizenship,target_countries," +
                    "remote_preference,cv_file_name,cv_text,account_id) values(?,?,?,?,?,?,?,?,?,?,?)", append(append(values, cvText), accountId));
    }

    private static Object[] append(Object[] values, Object value) {
        Object[] result = Arrays.copyOf(values, values.length + 1);
        result[values.length] = value;
        return result;
    }

    private CvCheck readCheck(String value) {
        return value == null ? null : json.readValue(value, CvCheck.class);
    }

    private static List<String> split(String value) {
        return value == null || value.isBlank() ? List.of() : Arrays.stream(value.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static List<String> clean(List<String> values, int maxItems, int maxLength) {
        if (values == null) return List.of();
        List<String> result = values.stream().filter(v -> v != null && !v.isBlank()).map(v -> v.strip().replace(",", " ")).distinct().toList();
        if (result.size() > maxItems || result.stream().anyMatch(v -> v.length() > maxLength))
            throw bad("Keep lists short: at most " + maxItems + " items of " + maxLength + " characters.");
        return result;
    }

    private static String optional(String value, int max, String label) {
        if (value == null || value.isBlank()) return null;
        String clean = value.strip();
        if (clean.length() > max) throw bad(label + " can be at most " + max + " characters.");
        return clean;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record ProfileInput(String headline, String summary, List<String> skills, Integer yearsExperience, List<String> languages,
                               String citizenship, List<String> targetCountries, String remotePreference) {}
}
