package com.deepwater.harbor;

import java.sql.Timestamp;
import java.util.HashMap;
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
 * The person's own application tracker. Harbor does not send anything to employers: "apply" opens the
 * original posting, and the person records here where each application stands.
 */
@Service
public class ApplicationService {
    static final Set<String> STATUSES = Set.of("SAVED", "APPLIED", "INTERVIEW", "OFFER", "REJECTED", "WITHDRAWN");
    private final JdbcTemplate db;
    private final JobCatalogService catalog;

    public ApplicationService(JdbcTemplate db, JobCatalogService catalog) {
        this.db = db;
        this.catalog = catalog;
    }

    @Transactional
    public Application track(String accountId, String jobId, String status, String note) {
        if (jobId == null || catalog.find(jobId) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This posting is no longer available.");
        String cleanStatus = status == null ? "SAVED" : status;
        if (!STATUSES.contains(cleanStatus)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown application status.");
        String cleanNote = note == null || note.isBlank() ? null : note.strip();
        if (cleanNote != null && cleanNote.length() > 1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keep the note under 1000 characters.");
        // A missing note keeps the one already saved; an empty note clears it.
        int updated = note == null
                ? db.update("update harbor_applications set status=?, updated_at=current_timestamp where account_id=? and job_id=?", cleanStatus, accountId, jobId)
                : db.update("update harbor_applications set status=?, note=?, updated_at=current_timestamp where account_id=? and job_id=?",
                        cleanStatus, cleanNote, accountId, jobId);
        if (updated == 0)
            db.update("insert into harbor_applications(id,account_id,job_id,status,note) values(?,?,?,?,?)",
                    UUID.randomUUID().toString(), accountId, jobId, cleanStatus, cleanNote);
        return list(accountId).stream().filter(a -> a.job().id().equals(jobId)).findFirst().orElseThrow();
    }

    @Transactional
    public void remove(String accountId, String jobId) {
        db.update("delete from harbor_applications where account_id=? and job_id=?", accountId, jobId);
    }

    public List<Application> list(String accountId) {
        return db.query("select a.status, a.note, a.created_at, a.updated_at, j.* from harbor_applications a " +
                        "join harbor_jobs j on j.id=a.job_id where a.account_id=? order by a.updated_at desc",
                (rs, row) -> new Application(JobSearchService.view(JobCatalogService.row(rs, row)), rs.getString("status"),
                        rs.getString("note"), instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at"))),
                accountId);
    }

    Map<String, String> statusesByJob(String accountId) {
        Map<String, String> result = new HashMap<>();
        db.query("select job_id, status from harbor_applications where account_id=?",
                rs -> { result.put(rs.getString(1), rs.getString(2)); }, accountId);
        return result;
    }

    private static java.time.Instant instant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }

    public record Application(JobSearchService.JobView job, String status, String note, java.time.Instant createdAt, java.time.Instant updatedAt) {}
}
