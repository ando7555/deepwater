package com.deepwater.amber;

import com.deepwater.amber.AmberRules.Intent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    /** The only Echo fact Amber reads: the language being learned, shown only if the person switches it on. */
    private static final Map<String, String> LANGUAGE_NAMES = Map.of("da", "Danish", "de", "German", "pl", "Polish");

    private final JdbcTemplate db;

    public AmberService(JdbcTemplate db) {
        this.db = db;
    }

    // ---------------------------------------------------------------- profile

    public MyProfile profile(String accountId) {
        String learning = echoLearning(accountId);
        var rows = db.query("select * from amber_profiles where account_id=?", (rs, row) -> stored(rs), accountId);
        if (rows.isEmpty()) return MyProfile.notJoined(learning);
        Stored p = rows.getFirst();
        return new MyProfile(true, p.memberId, p.name, p.age, p.area, names(p.intents), p.interests, p.about, p.visible,
                p.shareLearning, learning);
    }

    @Transactional
    public MyProfile save(String accountId, ProfileInput in) {
        String name = required(in.displayName(), "Add the first name others will see.", 40);
        if (in.age() == null || in.age() < MIN_AGE) throw bad("Amber is only for adults (18 or older).");
        if (in.age() > 120) throw bad("Check your age.");
        if (!Boolean.TRUE.equals(in.confirmAdult())) throw bad("Confirm that you are 18 or older.");
        String area = required(in.area(), "Choose your area, at city level.", 80);
        Set<Intent> intents = intents(in.intents());
        if (intents.isEmpty()) throw bad("Choose what you are looking for.");
        List<String> interests;
        try { interests = AmberRules.cleanInterests(in.interests()); }
        catch (IllegalArgumentException ex) { throw bad(ex.getMessage()); }
        String about = in.about() == null || in.about().isBlank() ? null : in.about().strip();
        if (about != null && about.length() > 300) throw bad("Keep the short intro under 300 characters.");
        boolean visible = in.visible() == null || in.visible();
        boolean shareLearning = Boolean.TRUE.equals(in.shareLearning());

        String intentText = String.join(",", intents.stream().map(Enum::name).toList());
        String interestText = String.join(",", interests);
        int updated = db.update("update amber_profiles set display_name=?, age=?, area=?, intents=?, interests=?, about=?, visible=?, " +
                        "share_learning=?, updated_at=current_timestamp where account_id=?",
                name, in.age(), area, intentText, interestText, about, visible, shareLearning, accountId);
        if (updated == 0)
            db.update("insert into amber_profiles(account_id,member_id,display_name,age,area,intents,interests,about,visible,share_learning) " +
                            "values(?,?,?,?,?,?,?,?,?,?)",
                    accountId, UUID.randomUUID().toString(), name, in.age(), area, intentText, interestText, about, visible, shareLearning);
        return profile(accountId);
    }

    /**
     * Leaves Amber: removes the profile, the person's invitations and their messages, and their pass/hide
     * choices. Blocks other people placed on this account stay, so leaving and rejoining cannot undo them,
     * and reports stay for review. The Deepwater account and Echo are untouched.
     */
    @Transactional
    public void leave(String accountId) {
        db.update("delete from amber_messages where invitation_id in (select id from amber_invitations where sender_id=? or recipient_id=?)",
                accountId, accountId);
        db.update("delete from amber_invitations where sender_id=? or recipient_id=?", accountId, accountId);
        db.update("delete from amber_decisions where account_id=? or target_account_id=?", accountId, accountId);
        db.update("delete from amber_profiles where account_id=?", accountId);
    }

    // ---------------------------------------------------------------- suggestions

    public List<Suggestion> suggestions(String accountId) {
        Stored me = requireMember(accountId);
        if (!me.visible) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Your profile is hidden, so suggestions are paused too. Turn on \"Visible in discovery\" to see people.");
        AmberRules.Facts mine = facts(me);
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
            AmberRules.Match match = AmberRules.match(mine, facts(other));
            if (match.eligible()) ranked.add(new Ranked(other, match));
        }
        ranked.sort((a, b) -> AmberRules.ORDER.compare(a.match, b.match));
        return ranked.stream().limit(MAX_SUGGESTIONS)
                .map(r -> new Suggestion(card(r.person), r.match.reasons(), r.match.sharedInterests())).toList();
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
    }

    // ---------------------------------------------------------------- shared with AmberConnections

    Stored requireMember(String accountId) {
        var rows = db.query("select * from amber_profiles where account_id=?", (rs, row) -> stored(rs), accountId);
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

    Card cardOf(String accountId) {
        var rows = db.query("select * from amber_profiles where account_id=?", (rs, row) -> stored(rs), accountId);
        return rows.isEmpty() ? null : card(rows.getFirst());
    }

    AmberRules.Match matchBetween(String viewer, String other) {
        Stored me = requireMember(viewer);
        var rows = db.query("select * from amber_profiles where account_id=?", (rs, row) -> stored(rs), other);
        return rows.isEmpty() ? null : AmberRules.match(facts(me), facts(rows.getFirst()));
    }

    // ---------------------------------------------------------------- helpers

    private Set<String> blockedEitherWay(String accountId) {
        Set<String> result = new HashSet<>(db.queryForList("select blocked_id from amber_blocks where blocker_id=?", String.class, accountId));
        result.addAll(db.queryForList("select blocker_id from amber_blocks where blocked_id=?", String.class, accountId));
        return result;
    }

    private Card card(Stored p) {
        return new Card(p.memberId, p.name, p.age, p.area, names(p.intents), p.interests, p.about,
                p.shareLearning ? echoLearning(p.accountId) : null);
    }

    private AmberRules.Facts facts(Stored p) {
        return new AmberRules.Facts(p.intents, p.area, p.interests, p.shareLearning ? echoLearning(p.accountId) : null);
    }

    private String echoLearning(String accountId) {
        var codes = db.queryForList("select language_code from echo_learner_profiles where account_id=?", String.class, accountId);
        return codes.isEmpty() ? null : LANGUAGE_NAMES.get(codes.getFirst());
    }

    private static Set<Intent> intents(List<String> raw) {
        Set<Intent> result = EnumSet.noneOf(Intent.class);
        if (raw != null) for (String value : raw) {
            try { result.add(Intent.valueOf(value)); }
            catch (IllegalArgumentException | NullPointerException ex) { throw bad("Unknown choice for what you are looking for."); }
        }
        return result;
    }

    private static List<String> names(Set<Intent> intents) { return intents.stream().map(Enum::name).toList(); }

    private static String required(String value, String message, int max) {
        if (value == null || value.isBlank()) throw bad(message);
        String clean = value.strip();
        if (clean.length() > max) throw bad("Keep it under " + max + " characters.");
        return clean;
    }

    static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    private static Stored stored(ResultSet rs) throws SQLException {
        Set<Intent> intents = EnumSet.noneOf(Intent.class);
        for (String value : rs.getString("intents").split(",")) if (!value.isBlank()) intents.add(Intent.valueOf(value));
        String interests = rs.getString("interests");
        return new Stored(rs.getString("account_id"), rs.getString("member_id"), rs.getString("display_name"), rs.getInt("age"),
                rs.getString("area"), intents, interests == null || interests.isBlank() ? List.of() : Arrays.asList(interests.split(",")),
                rs.getString("about"), rs.getBoolean("visible"), rs.getBoolean("share_learning"));
    }

    record Stored(String accountId, String memberId, String name, int age, String area, Set<Intent> intents,
                  List<String> interests, String about, boolean visible, boolean shareLearning) {}

    public record ProfileInput(String displayName, Integer age, Boolean confirmAdult, String area, List<String> intents,
                               List<String> interests, String about, Boolean visible, Boolean shareLearning) {}

    /** The person's own profile. {@code echoLearning} is what would be shown if they switch the Echo signal on. */
    public record MyProfile(boolean joined, String memberId, String displayName, Integer age, String area, List<String> intents,
                            List<String> interests, String about, boolean visible, boolean shareLearning, String echoLearning) {
        static MyProfile notJoined(String echoLearning) {
            return new MyProfile(false, null, null, null, null, List.of(), List.of(), null, true, false, echoLearning);
        }
    }

    /** What other members see. {@code learning} is null unless this person chose to show it. */
    public record Card(String memberId, String displayName, int age, String area, List<String> intents, List<String> interests,
                       String about, String learning) {}

    public record Suggestion(Card person, List<String> reasons, List<String> sharedInterests) {}
}
