package com.deepwater.echo;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Phrase cards a learner writes for themselves, reviewed on a calm Leitner schedule.
 * A miss moves a card back one box instead of to the start, so a bad day never wipes out progress.
 */
@Service
public class EchoCardService {
    /** Days until the next review for each box; box 0 is due straight away. */
    static final int[] BOX_INTERVAL_DAYS = {0, 1, 3, 7, 14, 30};
    static final int MAX_CARDS = 500;
    private static final int MAX_SIDE = 300;
    private static final String CARD_COLUMNS = "id,front,back,lesson_id,review_box,next_review_at";

    private final JdbcTemplate db;

    public EchoCardService(JdbcTemplate db) { this.db = db; }

    public List<EchoCard> cards(String accountId) {
        Instant now = Instant.now();
        return db.query("select " + CARD_COLUMNS + " from echo_cards where account_id=? and language_code=? " +
                        "order by next_review_at, created_at", (rs, row) -> card(rs, now), accountId, languageCode(accountId));
    }

    @Transactional
    public EchoCard save(String accountId, String front, String back, String lessonId) {
        String cleanFront = side(front, "Write the phrase");
        String cleanBack = side(back, "Write what the phrase means");
        Integer count = db.queryForObject("select count(*) from echo_cards where account_id=?", Integer.class, accountId);
        if (count != null && count >= MAX_CARDS)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have " + MAX_CARDS + " cards. Delete a few before adding more.");
        if (lessonId != null && !lessonId.isBlank() && db.queryForObject(
                "select count(*) from echo_lessons where id=? and review_status='PUBLISHED'", Integer.class, lessonId) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found.");
        String id = UUID.randomUUID().toString();
        db.update("insert into echo_cards(id,account_id,language_code,front,back,lesson_id,review_box,next_review_at) values(?,?,?,?,?,?,0,?)",
                id, accountId, languageCode(accountId), cleanFront, cleanBack,
                lessonId == null || lessonId.isBlank() ? null : lessonId, Timestamp.from(Instant.now()));
        return find(accountId, id);
    }

    @Transactional
    public EchoCard review(String accountId, String cardId, boolean knewIt) {
        EchoCard current = find(accountId, cardId);
        int box = knewIt ? Math.min(current.box() + 1, BOX_INTERVAL_DAYS.length - 1) : Math.max(current.box() - 1, 0);
        Instant next = Instant.now().plus(BOX_INTERVAL_DAYS[box], ChronoUnit.DAYS);
        db.update("update echo_cards set review_box=?, next_review_at=?, reviewed_at=current_timestamp where id=? and account_id=?",
                box, Timestamp.from(next), cardId, accountId);
        return find(accountId, cardId);
    }

    public boolean delete(String accountId, String cardId) {
        if (db.update("delete from echo_cards where id=? and account_id=?", cardId, accountId) != 1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found.");
        return true;
    }

    private EchoCard find(String accountId, String cardId) {
        Instant now = Instant.now();
        return db.query("select " + CARD_COLUMNS + " from echo_cards where id=? and account_id=?",
                        (rs, row) -> card(rs, now), cardId, accountId)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found."));
    }

    private String languageCode(String accountId) {
        return db.query("select language_code from echo_learner_profiles where account_id=?",
                        (rs, row) -> rs.getString(1), accountId)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Set up your Echo profile first."));
    }

    private static String side(String value, String message) {
        String clean = value == null ? "" : value.strip();
        if (clean.isEmpty() || clean.length() > MAX_SIDE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message + " in 1–" + MAX_SIDE + " characters.");
        return clean;
    }

    private static EchoCard card(java.sql.ResultSet rs, Instant now) throws java.sql.SQLException {
        Instant next = rs.getTimestamp("next_review_at").toInstant();
        return new EchoCard(rs.getString("id"), rs.getString("front"), rs.getString("back"), rs.getString("lesson_id"),
                rs.getInt("review_box"), next.toString(), !next.isAfter(now));
    }

    public record EchoCard(String id, String front, String back, String lessonId, int box, String nextReviewAt, boolean due) {}
}
