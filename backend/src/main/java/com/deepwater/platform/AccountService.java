package com.deepwater.platform;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account management that is not sign-in: a copy of everything Deepwater keeps about the person, and signing out
 * of every device at once. Deleting the account is {@link PlatformService#deleteAccount}.
 */
@Service
public class AccountService {
    /** Tables keyed by account_id whose rows belong to the person and are exported whole (minus secrets). */
    private static final List<String> OWN_TABLES = List.of("person_profiles", "person_languages", "echo_learner_profiles", "echo_cards",
            "echo_skill_states", "echo_attempts", "echo_audio_attempts", "exam_attempts", "harbor_profiles", "harbor_applications",
            "amber_profiles", "assistant_consents");

    private final JdbcTemplate db;

    public AccountService(JdbcTemplate db) { this.db = db; }

    /**
     * Everything stored about this account, as plain data. Left out: the password hash and sign-in tokens (secrets),
     * photo pixels (they are in the app; listed by id here), and anything about other people beyond what this person
     * wrote or chose (their own Amber messages, blocks and reports, by the other person's Amber member id).
     */
    public Map<String, Object> export(String accountId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", Instant.now().toString());
        out.put("account", clean(db.queryForMap("select id, name, email, email_verified, created_at from accounts where id=?", accountId)));
        for (String table : OWN_TABLES)
            out.put(table, db.queryForList("select * from " + table + " where account_id=?", accountId).stream().map(AccountService::clean).toList());
        out.put("person_photos", db.queryForList("select id, position, content_type, created_at from person_photos where account_id=? order by position",
                accountId).stream().map(AccountService::clean).toList());
        out.put("amber_invitations", db.queryForList("select i.id, i.status, i.created_at, i.responded_at, i.ended_at, " +
                        "case when i.sender_id=? then 'SENT' else 'RECEIVED' end as direction, p.member_id as other_member_id " +
                        "from amber_invitations i left join amber_profiles p on p.account_id = case when i.sender_id=? then i.recipient_id else i.sender_id end " +
                        "where i.sender_id=? or i.recipient_id=?", accountId, accountId, accountId, accountId)
                .stream().map(AccountService::clean).toList());
        out.put("amber_messages_written", db.queryForList("select invitation_id, body, created_at from amber_messages where sender_id=? order by created_at",
                accountId).stream().map(AccountService::clean).toList());
        out.put("amber_blocks_made", db.queryForList("select p.member_id as blocked_member_id, b.created_at from amber_blocks b " +
                "left join amber_profiles p on p.account_id=b.blocked_id where b.blocker_id=?", accountId).stream().map(AccountService::clean).toList());
        out.put("amber_reports_filed", db.queryForList("select r.reason, r.note, r.status, r.created_at from amber_reports r where r.reporter_id=?",
                accountId).stream().map(AccountService::clean).toList());
        return out;
    }

    /** Ends every session of this account, including the one making the request. Returns how many ended. */
    @Transactional
    public int signOutEverywhere(String accountId) {
        return db.update("delete from user_sessions where account_id=?", accountId);
    }

    private static final Object SKIP = new Object();

    /** One column as exported: dates as ISO text; secrets, binary data and internal account ids are skipped. */
    private static Object exportValue(String key, Object value) {
        boolean secret = key.equals("password_hash") || key.equals("token") || key.equals("account_id");
        if (secret || value instanceof byte[] || value instanceof java.sql.Clob || value instanceof java.sql.Blob) return SKIP;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant().toString();
        if (value instanceof java.sql.Date date) return date.toLocalDate().toString();
        if (value instanceof java.time.OffsetDateTime time) return time.toInstant().toString();
        return value;
    }

    /** Dates as ISO text, no binary columns, no internal account ids of other people. */
    private static Map<String, Object> clean(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (var entry : row.entrySet()) {
            String key = entry.getKey().toLowerCase(java.util.Locale.ROOT);
            Object value = exportValue(key, entry.getValue());
            if (value != SKIP) out.put(key, value);
        }
        return out;
    }
}
