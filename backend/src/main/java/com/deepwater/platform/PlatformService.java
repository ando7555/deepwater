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

    public PlatformService(JdbcTemplate db) { this.db = db; }

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
        var found = db.query("select id,name,email,password_hash from accounts where email=?",
                (rs, row) -> new String[]{rs.getString("id"), rs.getString("name"), rs.getString("email"), rs.getString("password_hash")},
                email.strip().toLowerCase());
        if (found.isEmpty() || !passwords.matches(password, found.getFirst()[3])) throw unauthorized();
        var account = found.getFirst();
        return session(account[0], account[1], account[2]);
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
