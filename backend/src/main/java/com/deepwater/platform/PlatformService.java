package com.deepwater.platform;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformService {
    private final JdbcTemplate db;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

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
        return Map.of("token", token, "name", name, "email", email, "accountId", id);
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue.");
    }
}
