package com.deepwater.platform;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformService {
    private final JdbcTemplate db;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    @Value("${deepwater.teacher.email:}") private String teacherEmail;

    private final LoginThrottle throttle;

    public PlatformService(JdbcTemplate db, LoginThrottle throttle) {
        this.db = db;
        this.throttle = throttle;
    }

    @Transactional
    public Map<String, String> register(String name, String email, String password) {
        if (name == null || name.isBlank() || name.length() > 160 || email == null
                || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || email.length() > 320
                || password == null || password.length() < 12 || password.length() > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Enter a name, valid email, and password of 12 to 72 characters.");
        }
        String id = UUID.randomUUID().toString();
        try {
            db.update("insert into accounts(id,name,email,password_hash) values(?,?,?,?)",
                    id, name.strip(), email.strip().toLowerCase(), passwords.encode(password));
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered.");
        }
        return session(id, name.strip(), email.strip().toLowerCase());
    }

    @Transactional
    public Map<String, String> login(String email, String password) {
        if (email == null || password == null) throw unauthorized();
        String normalizedEmail = email.strip().toLowerCase();
        if (throttle.isBlocked(normalizedEmail))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many sign-in attempts. Wait 15 minutes and try again.");
        var found = db.query("select id,name,email,password_hash from accounts where email=?",
                (rs, row) -> new String[]{rs.getString("id"), rs.getString("name"), rs.getString("email"), rs.getString("password_hash")},
                normalizedEmail);
        if (found.isEmpty() || !passwords.matches(password, found.getFirst()[3])) {
            throttle.recordFailure(normalizedEmail);
            throw unauthorized();
        }
        throttle.recordSuccess(normalizedEmail);
        var account = found.getFirst();
        return session(account[0], account[1], account[2]);
    }

    /** Finds the account for a verified Google identity, linking it by email or creating it as needed. */
    @Transactional
    public String loginOrRegisterWithGoogle(String googleSub, String email, String name) {
        var bySub = db.queryForList("select id from accounts where google_sub=?", String.class, googleSub);
        if (!bySub.isEmpty()) return bySub.getFirst();
        String normalizedEmail = email.strip().toLowerCase();
        var byEmail = db.queryForList("select id from accounts where email=?", String.class, normalizedEmail);
        if (!byEmail.isEmpty()) {
            // Google has verified this email; password sign-up has not. Whoever set the existing password may not own
            // the address, so linking removes that password and signs out its sessions. The owner continues with Google.
            String accountId = byEmail.getFirst();
            if (db.update("update accounts set google_sub=?, password_hash=null where id=? and google_sub is null", googleSub, accountId) == 1)
                db.update("delete from user_sessions where account_id=?", accountId);
            return byEmail.getFirst();
        }
        String displayName = name == null || name.isBlank() ? normalizedEmail.substring(0, normalizedEmail.indexOf('@')) : name.strip();
        if (displayName.length() > 160) displayName = displayName.substring(0, 160);
        String id = UUID.randomUUID().toString();
        db.update("insert into accounts(id,name,email,google_sub) values(?,?,?,?)", id, displayName, normalizedEmail, googleSub);
        return id;
    }

    /** A single-use code, valid for one minute, that the frontend exchanges for a session after the Google redirect. */
    public String issueLoginCode(String accountId) {
        db.update("delete from oauth_login_codes where expires_at<?", java.sql.Timestamp.from(Instant.now()));
        String code = UUID.randomUUID().toString();
        db.update("insert into oauth_login_codes(code,account_id,expires_at) values(?,?,?)", code, accountId,
                java.sql.Timestamp.from(Instant.now().plusSeconds(60)));
        return code;
    }

    @Transactional
    public Map<String, String> redeemLoginCode(String code) {
        if (code == null) throw unauthorized();
        var accounts = db.queryForList("select account_id from oauth_login_codes where code=? and expires_at>?",
                String.class, code, java.sql.Timestamp.from(Instant.now()));
        if (accounts.isEmpty() || db.update("delete from oauth_login_codes where code=?", code) != 1) throw unauthorized();
        var account = db.queryForMap("select id,name,email from accounts where id=?", accounts.getFirst());
        return session((String) account.get("id"), (String) account.get("name"), (String) account.get("email"));
    }

    /**
     * Permanently deletes the signed-in account and its learning records. The person must retype their email.
     * Only a random account id stays behind, in the audit log, to record that the deletion happened.
     */
    @Transactional
    public void deleteAccount(String authorization, String confirmEmail) {
        String accountId = requireAccount(authorization);
        String email = db.queryForObject("select email from accounts where id=?", String.class, accountId);
        if (confirmEmail == null || !email.equalsIgnoreCase(confirmEmail.strip()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type your account email to confirm deletion.");
        if ("TEACHER".equals(role(accountId)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The teacher account owns published lessons and cannot be deleted here.");
        db.update("update echo_lessons set created_by=null where created_by=?", accountId);
        db.update("update echo_lessons set approved_by=null where approved_by=?", accountId);
        for (String table : new String[]{"echo_audio_attempts", "echo_attempts", "echo_skill_states",
                "echo_learner_profiles", "oauth_login_codes", "user_sessions"})
            db.update("delete from " + table + " where account_id=?", accountId);
        db.update("delete from accounts where id=?", accountId);
        db.update("insert into audit_events(id,event_type,aggregate_id) values(?,?,?)",
                UUID.randomUUID().toString(), "ACCOUNT_DELETED", accountId);
    }

    public String requireAccount(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) throw unauthorized();
        String token = authorization.substring(7).strip();
        var accounts = db.query("select account_id from user_sessions where token=? and expires_at>?",
                (rs, row) -> rs.getString(1), token, java.sql.Timestamp.from(Instant.now()));
        if (accounts.isEmpty()) throw unauthorized();
        return accounts.getFirst();
    }

    /** Only the deployment-configured teacher account receives authoring access. */
    public String requireTeacher(String authorization) {
        String accountId = requireAccount(authorization);
        String email = db.queryForObject("select email from accounts where id=?", String.class, accountId);
        if (teacherEmail == null || teacherEmail.isBlank() || !email.equalsIgnoreCase(teacherEmail.strip()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Teacher access is not enabled for this account.");
        return accountId;
    }

    public String role(String accountId) {
        if (accountId == null || teacherEmail == null || teacherEmail.isBlank()) return "LEARNER";
        String email = db.queryForObject("select email from accounts where id=?", String.class, accountId);
        return email.equalsIgnoreCase(teacherEmail.strip()) ? "TEACHER" : "LEARNER";
    }

    /** Public lesson content can be browsed anonymously; a missing/invalid session maps to no learner. */
    public String accountIfAuthenticated(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        String token = authorization.substring(7).strip();
        return db.query("select account_id from user_sessions where token=? and expires_at>?",
                (rs, row) -> rs.getString(1), token, java.sql.Timestamp.from(Instant.now()))
                .stream().findFirst().orElse(null);
    }

    private Map<String, String> session(String id, String name, String email) {
        String token = UUID.randomUUID().toString();
        db.update("insert into user_sessions(token,account_id,expires_at) values(?,?,?)", token, id,
                java.sql.Timestamp.from(Instant.now().plusSeconds(60L * 60 * 24 * 7)));
        return Map.of("token", token, "name", name, "email", email, "accountId", id, "role", role(id));
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue.");
    }
}
