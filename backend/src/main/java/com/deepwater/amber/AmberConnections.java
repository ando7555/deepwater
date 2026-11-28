package com.deepwater.amber;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Invitations, the conversations they open, and leaving a conversation.
 *
 * <p>The rule this class exists for (AC-AMBER-3): nobody can read or send a message until the recipient
 * has explicitly accepted the invitation, and either person can end the conversation at any time.
 * A declined invitation is final and shows the sender only "closed", never a reason (AC-AMBER-4).
 */
@Service
public class AmberConnections {
    static final int MAX_PENDING_SENT = 10;
    static final int MAX_MESSAGES_PER_MINUTE = 20;
    static final int CLOSED_DAYS = 30;
    /** The only goodbyes Amber sends. The other person sees the sentence, never free text. */
    static final Map<String, String> CLOSING_NOTES = Map.of(
            "NOT_A_MATCH", "Thank you for the conversation. I don't feel we are a match, and I wish you well.",
            "NO_TIME", "I don't have time for Amber right now. Thank you, and take care.",
            "MET_SOMEONE", "I have met someone. Thank you for the conversation, and good luck.");
    private static final String PENDING = "PENDING";
    private static final String NOT_FOUND = "This invitation or conversation is not available.";

    private final JdbcTemplate db;
    private final AmberService amber;

    public AmberConnections(JdbcTemplate db, AmberService amber) {
        this.db = db;
        this.amber = amber;
    }

    @Transactional
    public InvitationView invite(String accountId, String memberId) {
        amber.requireMember(accountId);
        String target = amber.accountOf(memberId);
        if (target.equals(accountId)) throw AmberService.bad("That is your own profile.");
        Boolean visible = db.queryForObject("select visible from amber_profiles where account_id=?", Boolean.class, target);
        if (amber.blockedEitherWay(accountId, target) || !Boolean.TRUE.equals(visible))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This person is not available.");
        AmberRules.Match match = amber.matchBetween(accountId, target);
        if (match == null || !match.eligible())
            throw AmberService.bad("You can invite people who are looking for the same kind of connection as you.");
        var existing = db.queryForList("select status from amber_invitations where sender_id=? and recipient_id=? and status in ('PENDING','ACCEPTED','DECLINED')",
                String.class, accountId, target);
        if (!existing.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already invited this person.");
        if (count("select count(*) from amber_invitations where sender_id=? and recipient_id=? and status in ('PENDING','ACCEPTED')", target, accountId) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This person has already invited you. Look in Invitations.");
        if (count("select count(*) from amber_invitations where sender_id=? and status='PENDING'", accountId) >= MAX_PENDING_SENT)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "You have " + MAX_PENDING_SENT + " invitations waiting for an answer. Withdraw one or wait for answers first.");
        String id = UUID.randomUUID().toString();
        db.update("insert into amber_invitations(id,sender_id,recipient_id,status) values(?,?,?,'PENDING')", id, accountId, target);
        return new InvitationView(id, amber.cardOf(target, false), match.reasons(), PENDING, Instant.now());
    }

    public Invitations invitations(String accountId) {
        amber.requireMember(accountId);
        List<InvitationView> incoming = db.query("select id, sender_id, created_at from amber_invitations where recipient_id=? and status='PENDING' " +
                        "order by created_at desc", (rs, row) -> view(accountId, rs.getString(1), rs.getString(2), PENDING, rs.getTimestamp(3)),
                accountId).stream().filter(Objects::nonNull).toList();
        // The sender sees "closed" for a declined invitation: no reason, no time of the answer.
        List<InvitationView> outgoing = db.query("select id, recipient_id, status, created_at from amber_invitations where sender_id=? " +
                        "and status in ('PENDING','DECLINED') order by created_at desc",
                (rs, row) -> view(accountId, rs.getString(1), rs.getString(2), PENDING.equals(rs.getString(3)) ? PENDING : "CLOSED",
                        rs.getTimestamp(4)), accountId).stream().filter(Objects::nonNull).toList();
        return new Invitations(incoming, outgoing);
    }

    @Transactional
    public void respond(String accountId, String invitationId, boolean accept) {
        int updated = db.update("update amber_invitations set status=?, responded_at=current_timestamp where id=? and recipient_id=? and status='PENDING'",
                accept ? "ACCEPTED" : "DECLINED", invitationId, accountId);
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
    }

    @Transactional
    public void withdraw(String accountId, String invitationId) {
        int updated = db.update("update amber_invitations set status='WITHDRAWN', ended_at=current_timestamp where id=? and sender_id=? and status='PENDING'",
                invitationId, accountId);
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
    }

    public List<ConnectionView> connections(String accountId) {
        amber.requireMember(accountId);
        return db.query("select i.id, case when i.sender_id=? then i.recipient_id else i.sender_id end as other, i.responded_at, " +
                        "(select max(m.created_at) from amber_messages m where m.invitation_id=i.id) as last_message " +
                        "from amber_invitations i where (i.sender_id=? or i.recipient_id=?) and i.status='ACCEPTED' order by i.responded_at desc",
                (rs, row) -> {
                    AmberService.Card card = amber.cardOf(rs.getString("other"), true);
                    if (card == null) return null;
                    String id = rs.getString("id");
                    var last = db.queryForList("select sender_id from amber_messages where invitation_id=? order by created_at desc, id desc limit 1",
                            String.class, id);
                    Boolean lastMine = last.isEmpty() ? null : accountId.equals(last.getFirst());
                    return new ConnectionView(id, card, instant(rs.getTimestamp("responded_at")), instant(rs.getTimestamp("last_message")), lastMine);
                }, accountId, accountId, accountId).stream().filter(Objects::nonNull).toList();
    }

    /**
     * Conversations the other person ended in the last {@value #CLOSED_DAYS} days with a kind note, so nobody is left
     * wondering (no ghosting). Conversations ended by a block are not listed.
     */
    public List<ClosedView> closed(String accountId) {
        amber.requireMember(accountId);
        return db.query("select i.id, case when i.sender_id=? then i.recipient_id else i.sender_id end as other, i.ended_at, i.closing_note " +
                        "from amber_invitations i where (i.sender_id=? or i.recipient_id=?) and i.status='ENDED' and i.closing_note is not null " +
                        "and i.ended_by<>? and i.ended_at>? order by i.ended_at desc",
                (rs, row) -> {
                    String other = rs.getString("other");
                    if (amber.blockedEitherWay(accountId, other)) return null;
                    AmberService.Card card = amber.cardOf(other, false);
                    String note = CLOSING_NOTES.get(rs.getString("closing_note"));
                    return card == null || note == null ? null : new ClosedView(rs.getString("id"), card.displayName(), note, instant(rs.getTimestamp("ended_at")));
                }, accountId, accountId, accountId, accountId, Timestamp.from(Instant.now().minus(java.time.Duration.ofDays(CLOSED_DAYS))))
                .stream().filter(Objects::nonNull).toList();
    }

    public List<Message> messages(String accountId, String connectionId) {
        openConnection(accountId, connectionId);
        return db.query("select id, sender_id, body, created_at from amber_messages where invitation_id=? order by created_at, id",
                (rs, row) -> new Message(rs.getString(1), accountId.equals(rs.getString(2)), rs.getString(3), instant(rs.getTimestamp(4))),
                connectionId);
    }

    @Transactional
    public Message send(String accountId, String connectionId, String body) {
        openConnection(accountId, connectionId);
        if (body == null || body.isBlank()) throw AmberService.bad("Write a message first.");
        String clean = body.strip();
        if (clean.length() > 1000) throw AmberService.bad("Keep a message under 1000 characters.");
        if (count("select count(*) from amber_messages where sender_id=? and created_at>?", accountId,
                Timestamp.from(Instant.now().minusSeconds(60))) >= MAX_MESSAGES_PER_MINUTE)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Slow down a little and try again in a minute.");
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        db.update("insert into amber_messages(id,invitation_id,sender_id,body,created_at) values(?,?,?,?,?)",
                id, connectionId, accountId, clean, Timestamp.from(now));
        return new Message(id, true, clean, now);
    }

    /**
     * Either person can end the conversation. It closes for both, and neither can write again. A closing note is
     * optional and is one of {@link #CLOSING_NOTES} (fixed, kind sentences, so a goodbye can never be abusive).
     */
    @Transactional
    public void leave(String accountId, String connectionId, String note) {
        if (note != null && !CLOSING_NOTES.containsKey(note)) throw AmberService.bad("Choose one of the closing notes, or none.");
        int updated = db.update("update amber_invitations set status='ENDED', ended_at=current_timestamp, ended_by=?, closing_note=? where id=? " +
                "and status='ACCEPTED' and (sender_id=? or recipient_id=?)", accountId, note, connectionId, accountId, accountId);
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
    }

    /** Only the two people in an accepted, not-ended invitation get through; everyone else sees "not available". */
    private void openConnection(String accountId, String connectionId) {
        var rows = db.queryForList("select sender_id, recipient_id from amber_invitations where id=? and status='ACCEPTED' and (sender_id=? or recipient_id=?)",
                connectionId, accountId, accountId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        String sender = (String) rows.getFirst().get("sender_id");
        String other = accountId.equals(sender) ? (String) rows.getFirst().get("recipient_id") : sender;
        if (amber.blockedEitherWay(accountId, other)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
    }

    private InvitationView view(String viewer, String id, String other, String status, Timestamp created) {
        if (amber.blockedEitherWay(viewer, other)) return null;
        AmberService.Card card = amber.cardOf(other, false);
        if (card == null) return null;
        AmberRules.Match match = amber.matchBetween(viewer, other);
        return new InvitationView(id, card, match == null ? List.of() : match.reasons(), status, instant(created));
    }

    private int count(String sql, Object... args) {
        Integer value = db.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private static Instant instant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }

    public record InvitationView(String id, AmberService.Card person, List<String> reasons, String status, Instant createdAt) {}

    public record Invitations(List<InvitationView> incoming, List<InvitationView> outgoing) {}

    /** {@code lastMessageMine} is null before the first message; false means it is this person's turn to reply. */
    public record ConnectionView(String id, AmberService.Card person, Instant connectedAt, Instant lastMessageAt, Boolean lastMessageMine) {}

    public record ClosedView(String id, String displayName, String note, Instant closedAt) {}

    public record Message(String id, boolean mine, String body, Instant sentAt) {}
}
