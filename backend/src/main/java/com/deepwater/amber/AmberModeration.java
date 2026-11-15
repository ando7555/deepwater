package com.deepwater.amber;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The reviewer's side of Amber reports (App Store guideline 1.2: "timely responses to concerns").
 * A reviewer sees open reports with what the reported person wrote, then dismisses the report or removes the
 * person from Amber. Removal deletes their Amber profile and conversations and stops them joining again;
 * their Deepwater account, Echo and Harbor stay.
 *
 * <p>Who may review is an open owner decision; for now it is the teacher account (DEEPWATER_TEACHER_EMAIL).
 */
@Service
public class AmberModeration {
    static final int MESSAGES_SHOWN = 20;
    private final JdbcTemplate db;
    private final AmberService amber;

    public AmberModeration(JdbcTemplate db, AmberService amber) {
        this.db = db;
        this.amber = amber;
    }

    public List<ReportView> openReports() {
        return db.query("select r.id, r.reported_id, r.reason, r.note, r.created_at, p.display_name, p.about, p.area, p.interests, " +
                        "(select count(*) from amber_reports o where o.reported_id=r.reported_id) as times_reported " +
                        "from amber_reports r left join amber_profiles p on p.account_id=r.reported_id " +
                        "where r.status='OPEN' order by r.created_at",
                (rs, row) -> new ReportView(rs.getString("id"), rs.getString("reason"), rs.getString("note"),
                        instant(rs.getTimestamp("created_at")), rs.getInt("times_reported"),
                        rs.getString("display_name"), rs.getString("area"), rs.getString("about"), rs.getString("interests"),
                        messagesFromReported(rs.getString("id"))));
    }

    /** What the reported person wrote to the person who reported them, newest last. Nothing else is shown. */
    private List<String> messagesFromReported(String reportId) {
        var report = db.queryForMap("select reporter_id, reported_id from amber_reports where id=?", reportId);
        String reporter = (String) report.get("reporter_id");
        String reported = (String) report.get("reported_id");
        List<String> newestFirst = db.queryForList("select m.body from amber_messages m join amber_invitations i on i.id=m.invitation_id " +
                        "where m.sender_id=? and ((i.sender_id=? and i.recipient_id=?) or (i.sender_id=? and i.recipient_id=?)) " +
                        "order by m.created_at desc limit " + MESSAGES_SHOWN,
                String.class, reported, reporter, reported, reported, reporter);
        return newestFirst.reversed();
    }

    @Transactional
    public void decide(String reportId, String action) {
        var rows = db.queryForList("select reported_id from amber_reports where id=? and status='OPEN'", String.class, reportId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "That report is not open.");
        String reported = rows.getFirst();
        switch (action == null ? "" : action) {
            case "DISMISS" -> db.update("update amber_reports set status='DISMISSED' where id=?", reportId);
            case "REMOVE" -> {
                amber.leave(reported);
                if (db.queryForObject("select count(*) from amber_removed where account_id=?", Integer.class, reported) == 0)
                    db.update("insert into amber_removed(account_id,report_id) values(?,?)", reported, reportId);
                // Every open report about the same person is settled by the removal.
                db.update("update amber_reports set status='REMOVED' where reported_id=? and status='OPEN'", reported);
            }
            default -> throw AmberService.bad("Choose DISMISS or REMOVE.");
        }
    }

    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }

    public record ReportView(String id, String reason, String note, Instant createdAt, int timesReported,
                             String displayName, String area, String about, String interests, List<String> messages) {}
}
