package com.deepwater.platform;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The one Deepwater profile. It belongs to the account, not to a product, so Echo, Harbor and Amber all read the
 * same details. Two parts:
 * <ul>
 *   <li>Private: the account name, email, phone, country and why the person is learning. Never shown to anyone.</li>
 *   <li>Public when a product shows it: the first name others see, age (from the birth date), city, a short bio,
 *       the languages the person knows, and photos. Today only Amber shows them, and only after the person joins
 *       Amber; each product says on screen what it shows.</li>
 * </ul>
 */
@Service
public class PersonProfileService {
    public static final int MAX_LANGUAGES = 10;
    public static final Set<String> LEVELS = Set.of("NATIVE", "FLUENT", "GOOD", "BASIC");

    private final JdbcTemplate db;
    private final PersonPhotoService photos;

    public PersonProfileService(JdbcTemplate db, PersonPhotoService photos) {
        this.db = db;
        this.photos = photos;
    }

    public PersonProfile profile(String accountId) {
        PersonProfile base = db.queryForObject("select a.name,a.email,a.email_verified,p.phone,p.city,p.country,p.native_language,p.about," +
                        "p.public_name,p.birth_date,p.bio from accounts a left join person_profiles p on p.account_id=a.id where a.id=?",
                (rs, row) -> {
                    java.sql.Date birth = rs.getDate("birth_date");
                    LocalDate birthDate = birth == null ? null : birth.toLocalDate();
                    return new PersonProfile(rs.getString("name"), rs.getString("email"), rs.getBoolean("email_verified"),
                            rs.getString("phone"), rs.getString("city"), rs.getString("country"),
                            rs.getString("native_language"), rs.getString("about"), rs.getString("public_name"),
                            birthDate == null ? null : birthDate.toString(), ageOn(birthDate, today()), rs.getString("bio"),
                            List.of(), List.of());
                }, accountId);
        return base.with(languages(accountId), photos.list(accountId));
    }

    @Transactional
    public PersonProfile update(String accountId, PersonProfileInput input) {
        String name = input.name() == null ? "" : input.name().strip();
        if (name.isEmpty() || name.length() > 160)
            throw bad("Enter your name in 1–160 characters.");
        String phone = normalizePhone(input.phone());
        String city = optional(input.city(), 120, "City");
        String country = optional(input.country(), 80, "Country");
        String nativeLanguage = optional(input.nativeLanguage(), 60, "Native language");
        String about = optional(input.about(), 500, "Why you are learning");
        String publicName = optional(input.publicName(), 40, "The name others see");
        LocalDate birthDate = birthDate(input.birthDate());
        String bio = optional(input.bio(), 500, "Bio");
        db.update("update accounts set name=? where id=?", name, accountId);
        ensureRow(accountId);
        db.update("update person_profiles set phone=?,city=?,country=?,native_language=?,about=?,public_name=?,birth_date=?,bio=?," +
                        "updated_at=current_timestamp where account_id=?",
                phone, city, country, nativeLanguage, about, publicName, birthDate == null ? null : java.sql.Date.valueOf(birthDate), bio, accountId);
        // Older screens send no language list; that keeps the saved list as it is.
        if (input.languages() != null) saveLanguages(accountId, input.languages());
        return profile(accountId);
    }

    /**
     * Sets only the public fields a product form collected (Amber's join form), leaving the rest of the profile as it is.
     * A null argument means "not on that form".
     */
    @Transactional
    public void updatePublic(String accountId, String publicName, String birthDate, String city, String bio) {
        ensureRow(accountId);
        if (publicName != null) db.update("update person_profiles set public_name=? where account_id=?", optional(publicName, 40, "The name others see"), accountId);
        if (birthDate != null) {
            LocalDate date = birthDate(birthDate);
            db.update("update person_profiles set birth_date=? where account_id=?", date == null ? null : java.sql.Date.valueOf(date), accountId);
        }
        if (city != null) db.update("update person_profiles set city=? where account_id=?", optional(city, 120, "City"), accountId);
        if (bio != null) db.update("update person_profiles set bio=? where account_id=?", optional(bio, 500, "Bio"), accountId);
        db.update("update person_profiles set updated_at=current_timestamp where account_id=?", accountId);
    }

    /** Saves the optional phone number given at sign-up. */
    public void savePhone(String accountId, String phone) {
        if (phone == null) return;
        ensureRow(accountId);
        db.update("update person_profiles set phone=? where account_id=?", phone, accountId);
    }

    public List<KnownLanguage> languages(String accountId) {
        return db.query("select language, level from person_languages where account_id=? order by position",
                (rs, row) -> new KnownLanguage(rs.getString(1), rs.getString(2)), accountId);
    }

    /**
     * Accepts an international number with optional spaces, dashes, dots or brackets and keeps
     * a leading + and the digits. Blank means "no phone number".
     */
    public String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String compact = phone.strip().replaceAll("[\\s().-]", "");
        if (!compact.matches("\\+?\\d{6,15}"))
            throw bad("Enter a phone number with 6–15 digits, for example +45 12 34 56 78.");
        return compact;
    }

    /** Whole years between the birth date and the given day; null when there is no birth date. */
    public static Integer ageOn(LocalDate birthDate, LocalDate day) {
        return birthDate == null ? null : Period.between(birthDate, day).getYears();
    }

    private void saveLanguages(String accountId, List<KnownLanguage> raw) {
        List<KnownLanguage> clean = cleanLanguages(raw);
        db.update("delete from person_languages where account_id=?", accountId);
        for (int i = 0; i < clean.size(); i++)
            db.update("insert into person_languages(account_id,language,level,position) values(?,?,?,?)",
                    accountId, clean.get(i).language(), clean.get(i).level(), i);
    }

    /** Trims names, drops blanks and case-insensitive duplicates, and checks the level of each language. */
    public static List<KnownLanguage> cleanLanguages(List<KnownLanguage> raw) {
        List<KnownLanguage> clean = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (KnownLanguage language : raw) {
            if (language == null || language.language() == null || language.language().isBlank()) continue;
            String name = language.language().strip().replaceAll("\\s+", " ");
            if (name.length() > 40) throw bad("Keep each language name under 40 characters.");
            String level = language.level() == null ? "" : language.level().strip().toUpperCase(Locale.ROOT);
            if (!LEVELS.contains(level)) throw bad("Choose how well you know " + name + ".");
            if (seen.add(name.toLowerCase(Locale.ROOT))) clean.add(new KnownLanguage(name, level));
        }
        if (clean.size() > MAX_LANGUAGES) throw bad("List up to " + MAX_LANGUAGES + " languages.");
        return clean;
    }

    private void ensureRow(String accountId) {
        Integer rows = db.queryForObject("select count(*) from person_profiles where account_id=?", Integer.class, accountId);
        if (rows == null || rows == 0) db.update("insert into person_profiles(account_id) values(?)", accountId);
    }

    private static LocalDate birthDate(String value) {
        if (value == null || value.isBlank()) return null;
        LocalDate date;
        try { date = LocalDate.parse(value.strip()); }
        catch (DateTimeParseException _) { throw bad("Enter your birth date as year-month-day, for example 1994-05-27."); }
        if (date.isAfter(today()) || Period.between(date, today()).getYears() > 120) throw bad("Check your birth date.");
        return date;
    }

    private static LocalDate today() { return LocalDate.now(ZoneOffset.UTC); }

    private static String optional(String value, int max, String label) {
        if (value == null || value.isBlank()) return null;
        String clean = value.strip();
        if (clean.length() > max) throw bad(label + " can be at most " + max + " characters.");
        return clean;
    }

    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    public record KnownLanguage(String language, String level) {}

    /**
     * @param publicName the first name other people see in products that show the profile (Amber)
     * @param age        whole years from {@code birthDate}; null without a birth date
     */
    public record PersonProfile(String name, String email, boolean emailVerified, String phone, String city, String country,
                                String nativeLanguage, String about, String publicName, String birthDate, Integer age, String bio,
                                List<KnownLanguage> languages, List<PersonPhotoService.Photo> photos) {
        PersonProfile with(List<KnownLanguage> languages, List<PersonPhotoService.Photo> photos) {
            return new PersonProfile(name, email, emailVerified, phone, city, country, nativeLanguage, about, publicName, birthDate, age, bio,
                    languages, photos);
        }
    }

    public record PersonProfileInput(String name, String phone, String city, String country, String nativeLanguage, String about,
                                     String publicName, String birthDate, String bio, List<KnownLanguage> languages) {}
}
