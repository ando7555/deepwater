package com.deepwater.platform;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Issues single-use, 24-hour email verification links. Only a SHA-256 hash of each token is stored,
 * so a database leak does not expose working links.
 */
@Service
public class EmailVerificationService {
    static final long TOKEN_HOURS = 24;
    static final long RESEND_SECONDS = 60;

    private final JdbcTemplate db;
    private final VerificationMailer mailer;
    private final SecureRandom random = new SecureRandom();
    @Value("${deepwater.frontend.base-url:http://localhost:5173}") private String frontendBaseUrl;

    public EmailVerificationService(JdbcTemplate db, VerificationMailer mailer) {
        this.db = db;
        this.mailer = mailer;
    }

    /** Creates a fresh link and emails it. Mail problems are logged, never thrown, so sign-up still succeeds. */
    public void sendVerification(String accountId) {
        var account = db.queryForMap("select name,email,email_verified from accounts where id=?", accountId);
        if (Boolean.TRUE.equals(account.get("email_verified"))) return;
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        db.update("delete from email_verification_tokens where account_id=?", accountId);
        db.update("insert into email_verification_tokens(token_hash,account_id,expires_at) values(?,?,?)",
                hash(token), accountId, Timestamp.from(Instant.now().plusSeconds(TOKEN_HOURS * 3600)));
        mailer.sendVerification((String) account.get("email"), (String) account.get("name"),
                frontendBaseUrl.replaceAll("/+$", "") + "/?verify_email=" + token);
    }

    /** Sends a new link to a signed-in, unverified account, at most once a minute. */
    public void resend(String accountId) {
        Integer recent = db.queryForObject("select count(*) from email_verification_tokens where account_id=? and created_at>?",
                Integer.class, accountId, Timestamp.from(Instant.now().minusSeconds(RESEND_SECONDS)));
        if (recent != null && recent > 0)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "A link was just sent. Check your inbox, or try again in a minute.");
        sendVerification(accountId);
    }

    @Transactional
    public void verify(String token) {
        if (token == null || token.isBlank()) throw invalid();
        var accounts = db.queryForList("select account_id from email_verification_tokens where token_hash=? and expires_at>?",
                String.class, hash(token), Timestamp.from(Instant.now()));
        if (accounts.isEmpty()) throw invalid();
        String accountId = accounts.getFirst();
        db.update("delete from email_verification_tokens where account_id=?", accountId);
        db.update("update accounts set email_verified=true where id=?", accountId);
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "This confirmation link is invalid or has expired. Request a new one.");
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
