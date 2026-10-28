package com.deepwater.platform;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Personal details that belong to the Deepwater account, not to one product, so Echo and a
 * future Amber profile can both read them. Every field except the name is optional and private.
 */
@Service
public class PersonProfileService {
    private final JdbcTemplate db;

    public PersonProfileService(JdbcTemplate db) { this.db = db; }

    public PersonProfile profile(String accountId) {
        return db.queryForObject("select a.name,a.email,a.email_verified,p.phone,p.city,p.country,p.native_language,p.about " +
                        "from accounts a left join person_profiles p on p.account_id=a.id where a.id=?",
                (rs, row) -> new PersonProfile(rs.getString("name"), rs.getString("email"), rs.getBoolean("email_verified"),
                        rs.getString("phone"), rs.getString("city"), rs.getString("country"),
                        rs.getString("native_language"), rs.getString("about")), accountId);
    }

    @Transactional
    public PersonProfile update(String accountId, PersonProfileInput input) {
        String name = input.name() == null ? "" : input.name().strip();
        if (name.isEmpty() || name.length() > 160)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter your name in 1–160 characters.");
        String phone = normalizePhone(input.phone());
        String city = optional(input.city(), 120, "City");
        String country = optional(input.country(), 80, "Country");
        String nativeLanguage = optional(input.nativeLanguage(), 60, "Native language");
        String about = optional(input.about(), 500, "About you");
        db.update("update accounts set name=? where id=?", name, accountId);
        upsert(accountId, phone, city, country, nativeLanguage, about);
        return profile(accountId);
    }

    /** Saves the optional phone number given at sign-up. */
    public void savePhone(String accountId, String phone) {
        if (phone != null) upsert(accountId, phone, null, null, null, null);
    }

    /**
     * Accepts an international number with optional spaces, dashes, dots or brackets and keeps
     * a leading + and the digits. Blank means "no phone number".
     */
    public String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String compact = phone.strip().replaceAll("[\\s().-]", "");
        if (!compact.matches("\\+?[0-9]{6,15}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a phone number with 6–15 digits, for example +45 12 34 56 78.");
        return compact;
    }

    private void upsert(String accountId, String phone, String city, String country, String nativeLanguage, String about) {
        int updated = db.update("update person_profiles set phone=?,city=?,country=?,native_language=?,about=?,updated_at=current_timestamp where account_id=?",
                phone, city, country, nativeLanguage, about, accountId);
        if (updated == 0)
            db.update("insert into person_profiles(account_id,phone,city,country,native_language,about) values(?,?,?,?,?,?)",
                    accountId, phone, city, country, nativeLanguage, about);
    }

    private static String optional(String value, int max, String label) {
        if (value == null || value.isBlank()) return null;
        String clean = value.strip();
        if (clean.length() > max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " can be at most " + max + " characters.");
        return clean;
    }

    public record PersonProfile(String name, String email, boolean emailVerified, String phone, String city, String country,
                                String nativeLanguage, String about) {}
    public record PersonProfileInput(String name, String phone, String city, String country, String nativeLanguage, String about) {}
}
