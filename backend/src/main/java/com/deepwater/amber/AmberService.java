package com.deepwater.amber;

import com.deepwater.amber.AmberRules.Intent;
import com.deepwater.exam.ExamContent;
import com.deepwater.platform.PersonPhotoService;
import com.deepwater.platform.PersonProfileService;
import com.deepwater.platform.PersonProfileService.KnownLanguage;
import com.deepwater.platform.PersonProfileService.PersonProfile;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Amber profiles, suggestions, pass/hide, and the safety actions (block, report).
 *
 * <p>Joining is a separate, explicit step: a Deepwater account has no Amber presence until its owner saves
 * an Amber profile (AC-AMBER-1). Other members address a person only by the random {@code member_id}.
 */
@Service
public class AmberService {
    static final int MIN_AGE = 18;
    static final int PASS_DAYS = 30;
    static final int MAX_SUGGESTIONS = 20;
    static final Set<String> REPORT_REASONS = Set.of("UNWANTED_MESSAGES", "FAKE_PROFILE", "FEELS_UNSAFE", "UNDERAGE", "OTHER");
    /** Photos are seen only after both said yes (the default, owner's choice 2026-11-27), or by every member who sees the profile. */
    static final Set<String> PHOTO_VISIBILITY = Set.of("CONNECTED", "MEMBERS");
    private static final Map<String, String> LANGUAGE_NAMES = Map.of("da", "Danish", "de", "German", "pl", "Polish");
    private static final Map<String, String> ECHO_LEVEL_WORDS = Map.of("new", "just starting", "some", "knows some", "comfortable", "comfortable");
    private static final Set<String> SPEAKS_WELL = Set.of("NATIVE", "FLUENT");

    private final JdbcTemplate db;
    private final ApplicationEventPublisher events;
    private final PersonProfileService people;
    private final PersonPhotoService photos;
    private final ExamContent exams;

    public AmberService(JdbcTemplate db, ApplicationEventPublisher events, PersonProfileService people, PersonPhotoService photos,
                        ExamContent exams) {
        this.db = db;
        this.events = events;
        this.people = people;
        this.photos = photos;
        this.exams = exams;
    }

    // ---------------------------------------------------------------- profile

    /**
     * The person's own Amber profile. Name, age, area, intro, languages and photos come from the Deepwater profile;
     * the rest is Amber's own. {@code learningDetails} and {@code usesHarbor} are what <em>would</em> be shown if the
     * matching switch is on.
     */
    public MyProfile profile(String accountId) {
        PersonProfile person = people.profile(accountId);
        List<Learning> learning = learning(accountId);
        boolean harbor = usesHarbor(accountId);
        var rows = find(accountId);
        Stored p = rows.isEmpty() ? null : rows.getFirst();
        Integer age = person.age() != null ? person.age() : p == null ? null : p.legacyAge;
        List<String> missing = new ArrayList<>();
        if (person.publicName() == null) missing.add("the first name others see");
        if (age == null) missing.add("your birth date");
        if (person.city() == null) missing.add("your city");
        String firstLearning = learning.isEmpty() ? null : learning.getFirst().language();
        if (p == null) return new MyProfile(false, null, person.publicName(), age, person.birthDate(), person.city(), List.of(), List.of(),
                person.bio(), true, false, true, false, "CONNECTED", firstLearning, learning, person.languages(), person.photos(), harbor, missing);
        return new MyProfile(true, p.memberId, person.publicName(), age, person.birthDate(), person.city(), names(p.intents), p.interests,
                person.bio(), p.visible, p.shareLearning, p.shareLanguages, p.shareHarbor, p.photosVisibility, firstLearning, learning,
                person.languages(), person.photos(), harbor, missing);
    }

    /**
     * Joins Amber or saves the Amber settings. Name, birth date, area and intro, when sent, are saved on the Deepwater
     * profile (one profile for every product); the older {@code age} field is kept for people without a birth date.
     */
    @Transactional
    public MyProfile save(String accountId, ProfileInput in) {
        people.updatePublic(accountId, in.displayName(), in.birthDate(), in.area(), in.about());
        PersonProfile person = people.profile(accountId);
        var existing = find(accountId);
        Integer legacyAge = in.age() != null ? in.age() : existing.isEmpty() ? null : existing.getFirst().legacyAge;
        Integer age = person.age() != null ? person.age() : legacyAge;

        if (person.publicName() == null) throw bad("Add the first name others will see.");
        if (age == null) throw bad("Add your birth date. Amber is only for adults (18 or older).");
        if (age < MIN_AGE) throw bad("Amber is only for adults (18 or older).");
        if (age > 120) throw bad("Check your age.");
        if (!Boolean.TRUE.equals(in.confirmAdult())) throw bad("Confirm that you are 18 or older.");
        if (person.city() == null) throw bad("Choose your area, at city level.");
        Set<Intent> intents = intents(in.intents());
        if (intents.isEmpty()) throw bad("Choose what you are looking for.");
        List<String> interests;
        try { interests = AmberRules.cleanInterests(in.interests()); }
        catch (IllegalArgumentException ex) { throw bad(ex.getMessage()); }
        boolean visible = in.visible() == null || in.visible();
        boolean shareLearning = Boolean.TRUE.equals(in.shareLearning());
        boolean shareLanguages = in.shareLanguages() == null || in.shareLanguages();
        boolean shareHarbor = Boolean.TRUE.equals(in.shareHarbor());
        String photosVisibility = in.photosVisibility() == null ? "CONNECTED" : in.photosVisibility();
        if (!PHOTO_VISIBILITY.contains(photosVisibility)) throw bad("Choose who can see your photos.");

        String intentText = String.join(",", intents.stream().map(Enum::name).toList());
        String interestText = String.join(",", interests);
        int updated = db.update("update amber_profiles set age=?, intents=?, interests=?, visible=?, share_learning=?, share_languages=?, " +
                        "share_harbor=?, photos_visibility=?, updated_at=current_timestamp where account_id=?",
                legacyAge, intentText, interestText, visible, shareLearning, shareLanguages, shareHarbor, photosVisibility, accountId);
        if (updated == 0)
            db.update("insert into amber_profiles(account_id,member_id,age,intents,interests,visible,share_learning,share_languages,share_harbor," +
                            "photos_visibility) values(?,?,?,?,?,?,?,?,?,?)",
                    accountId, UUID.randomUUID().toString(), legacyAge, intentText, interestText, visible, shareLearning, shareLanguages,
                    shareHarbor, photosVisibility);
        return profile(accountId);
    }

    /**
     * Leaves Amber: removes the profile, the person's invitations and their messages, and their pass/hide
     * choices. Blocks other people placed on this account stay, so leaving and rejoining cannot undo them,
     * and reports stay for review. The Deepwater account and profile (with its photos), Echo and Harbor are untouched.
     */
    @Transactional
    public void leave(String accountId) {
        db.update("delete from amber_messages where invitation_id in (select id from amber_invitations where sender_id=? or recipient_id=?)",
                accountId, accountId);
        db.update("delete from amber_invitations where sender_id=? or recipient_id=?", accountId, accountId);
        db.update("delete from amber_decisions where account_id=? or target_account_id=?", accountId, accountId);
        db.update("delete from amber_profiles where account_id=?", accountId);
    }

    /**
     * One photo of another member, if this viewer may see it: never across a block, and otherwise only after both
     * said yes, unless that member chose to show photos to everyone who sees their profile. Anything else looks
     * exactly like a photo that does not exist.
     */
    public byte[] photo(String viewer, String memberId, String photoId) {
        requireMember(viewer);
        String target = accountOf(memberId);
        if (!target.equals(viewer)) {
            Stored them = find(target).getFirst();
            boolean allowed = !blockedEitherWay(viewer, target)
                    && (connected(viewer, target) || ("MEMBERS".equals(them.photosVisibility) && them.visible));
            if (!allowed) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found.");
        }
        return photos.bytes(target, photoId);
    }

    // ---------------------------------------------------------------- suggestions

    public List<Suggestion> suggestions(String accountId) {
        Stored me = requireMember(accountId);
        if (!me.visible) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Your profile is hidden, so suggestions are paused too. Turn on \"Visible in discovery\" to see people.");
        AmberRules.Facts mine = facts(me, true);
        Set<String> excluded = new HashSet<>(blockedEitherWay(accountId));
        excluded.addAll(db.queryForList("select target_account_id from amber_decisions where account_id=? and (decision='HIDE' or decided_at>?)",
                String.class, accountId, java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(PASS_DAYS)))));
        // Anyone with a live invitation in either direction, and anyone who already declined mine, is not suggested again.
        excluded.addAll(db.queryForList("select recipient_id from amber_invitations where sender_id=? and status in ('PENDING','ACCEPTED','DECLINED')",
                String.class, accountId));
        excluded.addAll(db.queryForList("select sender_id from amber_invitations where recipient_id=? and status in ('PENDING','ACCEPTED')",
                String.class, accountId));

        record Ranked(Stored person, AmberRules.Match match) {}
        List<Ranked> ranked = new ArrayList<>();
        for (Stored other : db.query("select * from amber_profiles where visible=true and account_id<>? order by updated_at desc",
                (rs, row) -> stored(rs), accountId)) {
            if (excluded.contains(other.accountId)) continue;
            AmberRules.Match match = AmberRules.match(mine, facts(other, false));
            if (match.eligible()) ranked.add(new Ranked(other, match));
        }
        ranked.sort((a, b) -> AmberRules.ORDER.compare(a.match, b.match));
        // People whose Deepwater profile lost a required field (name, age, area) are not shown until they add it back.
        return ranked.stream().map(r -> new Suggestion(card(r.person, false), r.match.reasons(), r.match.sharedInterests()))
                .filter(s -> s.person().complete()).limit(MAX_SUGGESTIONS).toList();
    }

    @Transactional
    public void decide(String accountId, String memberId, String decision) {
        requireMember(accountId);
        String target = accountOf(memberId);
        if (target.equals(accountId)) throw bad("That is your own profile.");
        int updated = db.update("update amber_decisions set decision=?, decided_at=current_timestamp where account_id=? and target_account_id=?",
                decision, accountId, target);
        if (updated == 0)
            db.update("insert into amber_decisions(account_id,target_account_id,decision) values(?,?,?)", accountId, target, decision);
    }

    // ---------------------------------------------------------------- safety

    /** Blocks a member: they disappear for each other, and any invitation or conversation between them ends. */
    @Transactional
    public void block(String accountId, String memberId) {
        requireMember(accountId);
        String target = accountOf(memberId);
        if (target.equals(accountId)) throw bad("That is your own profile.");
        if (db.queryForObject("select count(*) from amber_blocks where blocker_id=? and blocked_id=?", Integer.class, accountId, target) == 0)
            db.update("insert into amber_blocks(blocker_id,blocked_id) values(?,?)", accountId, target);
        db.update("update amber_invitations set status='ENDED', ended_at=current_timestamp where status in ('PENDING','ACCEPTED') and " +
                "((sender_id=? and recipient_id=?) or (sender_id=? and recipient_id=?))", accountId, target, target, accountId);
    }

    /** Records a report for human review and blocks the member. The reported person is never told. */
    @Transactional
    public void report(String accountId, String memberId, String reason, String note) {
        if (reason == null || !REPORT_REASONS.contains(reason)) throw bad("Choose a reason for the report.");
        String cleanNote = note == null || note.isBlank() ? null : note.strip();
        if (cleanNote != null && cleanNote.length() > 1000) throw bad("Keep the note under 1000 characters.");
        block(accountId, memberId);
        db.update("insert into amber_reports(id,reporter_id,reported_id,reason,note) values(?,?,?,?,?)",
                UUID.randomUUID().toString(), accountId, accountOf(memberId), reason, cleanNote);
        events.publishEvent(new AmberReportFiled(reason));
    }

    // ---------------------------------------------------------------- shared with AmberConnections

    Stored requireMember(String accountId) {
        var rows = find(accountId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Create your Amber profile first.");
        return rows.getFirst();
    }

    /** Unknown and blocked members get the same answer, so a block cannot be detected by probing. */
    String accountOf(String memberId) {
        var rows = db.queryForList("select account_id from amber_profiles where member_id=?", String.class, memberId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This person is not available.");
        return rows.getFirst();
    }

    boolean blockedEitherWay(String a, String b) {
        return db.queryForObject("select count(*) from amber_blocks where (blocker_id=? and blocked_id=?) or (blocker_id=? and blocked_id=?)",
                Integer.class, a, b, b, a) > 0;
    }

    /** What the viewer sees of this member; {@code connected} means both said yes, which unlocks photos. */
    Card cardOf(String accountId, boolean connected) {
        var rows = find(accountId);
        return rows.isEmpty() ? null : card(rows.getFirst(), connected);
    }

    AmberRules.Match matchBetween(String viewer, String other) {
        Stored me = requireMember(viewer);
        var rows = find(other);
        return rows.isEmpty() ? null : AmberRules.match(facts(me, true), facts(rows.getFirst(), false));
    }

    boolean connected(String a, String b) {
        return db.queryForObject("select count(*) from amber_invitations where status='ACCEPTED' and " +
                "((sender_id=? and recipient_id=?) or (sender_id=? and recipient_id=?))", Integer.class, a, b, b, a) > 0;
    }

    // ---------------------------------------------------------------- helpers

    private List<Stored> find(String accountId) {
        return db.query("select * from amber_profiles where account_id=?", (rs, row) -> stored(rs), accountId);
    }

    private Set<String> blockedEitherWay(String accountId) {
        Set<String> result = new HashSet<>(db.queryForList("select blocked_id from amber_blocks where blocker_id=?", String.class, accountId));
        result.addAll(db.queryForList("select blocker_id from amber_blocks where blocked_id=?", String.class, accountId));
        return result;
    }

    private Card card(Stored p, boolean connected) {
        PersonProfile person = people.profile(p.accountId);
        Integer age = person.age() != null ? person.age() : p.legacyAge;
        List<Learning> learning = p.shareLearning ? learning(p.accountId) : List.of();
        List<String> photoIds = person.photos().stream().map(PersonPhotoService.Photo::id).toList();
        boolean showPhotos = connected || "MEMBERS".equals(p.photosVisibility);
        return new Card(p.memberId, person.publicName(), age, person.city(), names(p.intents), p.interests, person.bio(),
                learning.isEmpty() ? null : learning.getFirst().language(), learning,
                p.shareLanguages ? person.languages() : List.of(), p.shareHarbor ? usesHarbor(p.accountId) : null,
                showPhotos ? photoIds : List.of(), photoIds.size(), !showPhotos && !photoIds.isEmpty(), person.emailVerified());
    }

    /**
     * What one person brings to matching. The viewer's own Echo languages and spoken languages always count for
     * their own view; another person's count only if that person switched them on.
     */
    private AmberRules.Facts facts(Stored p, boolean viewer) {
        PersonProfile person = people.profile(p.accountId);
        Set<String> learning = new HashSet<>();
        if (viewer || p.shareLearning) for (Learning line : learning(p.accountId)) learning.add(line.language());
        Set<String> speaks = new HashSet<>();
        if (viewer || p.shareLanguages)
            for (KnownLanguage language : person.languages()) if (SPEAKS_WELL.contains(language.level())) speaks.add(language.language());
        return new AmberRules.Facts(p.intents, person.city(), p.interests, learning, p.shareLearning, speaks);
    }

    /**
     * The languages this person learns in Deepwater: the Echo course language with their own starting level, and every
     * language they took a practice exam in, with the latest estimate (which replaces the own level).
     */
    List<Learning> learning(String accountId) {
        Map<String, String> levels = new LinkedHashMap<>();
        for (var row : db.queryForList("select language_code, self_reported_level from echo_learner_profiles where account_id=?", accountId)) {
            String name = LANGUAGE_NAMES.get((String) row.get("language_code"));
            if (name != null) levels.put(name, ECHO_LEVEL_WORDS.getOrDefault((String) row.get("self_reported_level"), "learning") + " (own estimate)");
        }
        Set<String> seenExams = new HashSet<>();
        for (var row : db.queryForList("select exam_id, level_estimate from exam_attempts where account_id=? order by created_at desc", accountId)) {
            String examId = (String) row.get("exam_id");
            if (!seenExams.add(examId)) continue;
            String name;
            try { name = LANGUAGE_NAMES.get(exams.exam(examId).language()); }
            catch (ResponseStatusException ex) { continue; }
            if (name != null) levels.put(name, row.get("level_estimate") + " on a Deepwater practice exam");
        }
        return levels.entrySet().stream().map(e -> new Learning(e.getKey(), e.getValue())).toList();
    }

    private boolean usesHarbor(String accountId) {
        return db.queryForObject("select count(*) from harbor_profiles where account_id=?", Integer.class, accountId) > 0;
    }

    private static Set<Intent> intents(List<String> raw) {
        Set<Intent> result = EnumSet.noneOf(Intent.class);
        if (raw != null) for (String value : raw) {
            try { result.add(Intent.valueOf(value)); }
            catch (IllegalArgumentException | NullPointerException _) { throw bad("Unknown choice for what you are looking for."); }
        }
        return result;
    }

    private static List<String> names(Set<Intent> intents) { return intents.stream().map(Enum::name).toList(); }

    static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    private static Stored stored(ResultSet rs) throws SQLException {
        Set<Intent> intents = EnumSet.noneOf(Intent.class);
        for (String value : rs.getString("intents").split(",")) if (!value.isBlank()) intents.add(Intent.valueOf(value));
        String interests = rs.getString("interests");
        int age = rs.getInt("age");
        return new Stored(rs.getString("account_id"), rs.getString("member_id"), rs.wasNull() ? null : age, intents,
                interests == null || interests.isBlank() ? List.of() : Arrays.asList(interests.split(",")),
                rs.getBoolean("visible"), rs.getBoolean("share_learning"), rs.getBoolean("share_languages"), rs.getBoolean("share_harbor"),
                rs.getString("photos_visibility"));
    }

    /** Amber's own row. Name, area and intro live on the Deepwater profile; {@code legacyAge} is the typed age of the first version. */
    record Stored(String accountId, String memberId, Integer legacyAge, Set<Intent> intents, List<String> interests, boolean visible,
                  boolean shareLearning, boolean shareLanguages, boolean shareHarbor, String photosVisibility) {}

    /**
     * @param displayName,birthDate,area,about optional: saved on the Deepwater profile when sent
     * @param age            the first version's typed age, used only while there is no birth date
     * @param shareLanguages show the languages from the Deepwater profile (on unless switched off)
     * @param shareHarbor    show "Uses Harbor" (off unless switched on)
     * @param photosVisibility CONNECTED (default) or MEMBERS
     */
    public record ProfileInput(String displayName, Integer age, String birthDate, Boolean confirmAdult, String area, List<String> intents,
                               List<String> interests, String about, Boolean visible, Boolean shareLearning, Boolean shareLanguages,
                               Boolean shareHarbor, String photosVisibility) {}

    /** One language the person learns in Deepwater, with a short level text such as "B1 on a Deepwater practice exam". */
    public record Learning(String language, String level) {}

    /**
     * The person's own profile. {@code echoLearning}, {@code learningDetails} and {@code usesHarbor} are what would be
     * shown if the matching switch is on; {@code missing} lists Deepwater profile fields Amber still needs.
     */
    public record MyProfile(boolean joined, String memberId, String displayName, Integer age, String birthDate, String area,
                            List<String> intents, List<String> interests, String about, boolean visible, boolean shareLearning,
                            boolean shareLanguages, boolean shareHarbor, String photosVisibility, String echoLearning,
                            List<Learning> learningDetails, List<KnownLanguage> languages, List<PersonPhotoService.Photo> photos,
                            boolean usesHarbor, List<String> missing) {}

    /**
     * What other members see. Optional parts are empty or null unless this person switched them on.
     *
     * @param learning       the first Echo language, kept for the first version's screens
     * @param usesHarbor     null when not shown
     * @param photoIds       the photos this viewer may load; empty before both said yes unless shown to everyone
     * @param photosAfterYes true when photos exist but open only after both say yes
     */
    public record Card(String memberId, String displayName, Integer age, String area, List<String> intents, List<String> interests,
                       String about, String learning, List<Learning> learningDetails, List<KnownLanguage> languages, Boolean usesHarbor,
                       List<String> photoIds, int photoCount, boolean photosAfterYes, boolean emailConfirmed) {
        public boolean complete() { return displayName != null && age != null && area != null; }
    }

    public record Suggestion(Card person, List<String> reasons, List<String> sharedInterests) {}
}
