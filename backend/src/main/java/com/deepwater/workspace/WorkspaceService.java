package com.deepwater.workspace;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The AI workspace: tasks for the owner's AI roles, from proposal to done, with an activity log.
 *
 * <p>Rules that hold everywhere (REST and MCP alike):
 * <ul>
 *   <li>Only the owner account (deepwater.workspace.owner-email) can see or change anything here.</li>
 *   <li>Only the owner approves, rejects, accepts or sends back. No tool offers those moves, so an AI role can never
 *       approve its own work.</li>
 *   <li>An approval is bound to the exact task text. Editing an approved task needs a new approval.</li>
 *   <li>A task reaches review only with evidence (pull request link, test result lines).</li>
 *   <li>Repeating a move that already happened changes nothing and logs nothing (safe retries).</li>
 *   <li>Owner moves carry the version the owner saw; a stale version is refused with 409.</li>
 * </ul>
 */
@Service
public class WorkspaceService {
    public static final Set<String> PRODUCTS = Set.of("ECHO", "AMBER", "HARBOR", "ASK_DEEPWATER", "AI_FASHION", "PLATFORM");
    static final String OWNER = "owner";
    private static final int MAX_CRITERIA = 12;
    private static final int MAX_LIST = 200;
    private static final String SELECT = "select * from workspace_tasks";

    /** Who is acting: the owner in the workspace screen, or one of the AI roles through MCP. */
    public record Actor(String name, String roleId) {
        public static Actor owner() { return new Actor(OWNER, null); }
        public static Actor agent(String roleId) { return new Actor("agent:" + roleId, roleId); }
        boolean isOwner() { return roleId == null; }
    }

    public record TaskInput(String title, String product, String roleId, String objective, List<String> acceptanceCriteria) {}

    public record Event(String actor, String action, String detail, Instant at) {}

    public record TaskView(String id, String title, String product, String roleId, String roleName, String objective,
                           List<String> acceptanceCriteria, TaskStatus status, String proposedBy, Instant approvedAt,
                           String evidence, int version, Instant createdAt, Instant updatedAt, String handoff,
                           List<Event> events) {}

    private record Row(String id, String title, String product, String roleId, String objective, List<String> criteria,
                       TaskStatus status, String proposedBy, String approvedHash, Instant approvedAt, String evidence,
                       int version, Instant createdAt, Instant updatedAt) {}

    private final JdbcTemplate db;
    private final AgentRoles roles;
    private final String ownerEmail;

    public WorkspaceService(JdbcTemplate db, AgentRoles roles, @Value("${deepwater.workspace.owner-email:}") String ownerEmail) {
        this.db = db;
        this.roles = roles;
        this.ownerEmail = ownerEmail == null ? "" : ownerEmail.strip();
    }

    // ---------------------------------------------------------------- access

    public boolean isOwner(String accountId) {
        if (accountId == null || ownerEmail.isEmpty()) return false;
        var emails = db.queryForList("select email from accounts where id=?", String.class, accountId);
        return !emails.isEmpty() && ownerEmail.equalsIgnoreCase(emails.getFirst());
    }

    public void requireOwner(String accountId) {
        if (!isOwner(accountId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The AI workspace is only open to the owner account.");
    }

    // ---------------------------------------------------------------- reading

    public List<TaskView> tasks(String accountId, String status) {
        requireOwner(accountId);
        List<Row> rows = status == null || status.isBlank()
                ? db.query(SELECT + " order by updated_at desc limit " + MAX_LIST, this::row)
                : db.query(SELECT + " where status=? order by updated_at desc limit " + MAX_LIST, this::row, parseStatus(status).name());
        return rows.stream().map(r -> view(r, List.of())).toList();
    }

    public TaskView task(String accountId, String id) {
        requireOwner(accountId);
        return view(load(id), events(id));
    }

    // ---------------------------------------------------------------- moves

    @Transactional
    public TaskView propose(String accountId, Actor actor, TaskInput in) {
        requireOwner(accountId);
        if (!actor.isOwner()) requireTool(actor, "workspace_propose_task");
        TaskInput clean = validate(in);
        String id = UUID.randomUUID().toString();
        Timestamp now = Timestamp.from(Instant.now());
        db.update("insert into workspace_tasks(id,title,product,role_id,objective,acceptance_criteria,status,proposed_by,created_at,updated_at)"
                        + " values (?,?,?,?,?,?,?,?,?,?)", id, clean.title(), clean.product(), clean.roleId(), clean.objective(),
                String.join("\n", clean.acceptanceCriteria()), TaskStatus.PROPOSED.name(), actor.name(), now, now);
        log(id, actor, "PROPOSED", null);
        return task(accountId, id);
    }

    /** Owner edits the text. An approved task goes back to PROPOSED because the approval was for the old text. */
    @Transactional
    public TaskView edit(String accountId, String id, int version, TaskInput in) {
        requireOwner(accountId);
        Row row = load(id);
        checkVersion(row, version);
        if (row.status() != TaskStatus.PROPOSED && row.status() != TaskStatus.APPROVED)
            throw conflict("Only a proposed or approved task can be edited.");
        TaskInput clean = validate(in);
        boolean changed = !hash(clean).equals(hash(row));
        TaskStatus next = changed && row.status() == TaskStatus.APPROVED ? TaskStatus.PROPOSED : row.status();
        db.update("update workspace_tasks set title=?,product=?,role_id=?,objective=?,acceptance_criteria=?,status=?,"
                        + "approved_hash=?,approved_at=?,version=version+1,updated_at=? where id=?",
                clean.title(), clean.product(), clean.roleId(), clean.objective(), String.join("\n", clean.acceptanceCriteria()),
                next.name(), next == TaskStatus.PROPOSED ? null : row.approvedHash(),
                next == TaskStatus.PROPOSED || row.approvedAt() == null ? null : Timestamp.from(row.approvedAt()),
                Timestamp.from(Instant.now()), id);
        log(id, Actor.owner(), "EDITED", next != row.status() ? "Approval removed: the text changed." : null);
        return task(accountId, id);
    }

    @Transactional
    public TaskView approve(String accountId, String id, int version) {
        requireOwner(accountId);
        Row row = load(id);
        if (row.status() == TaskStatus.APPROVED) return view(row, events(id));
        checkVersion(row, version);
        move(row, TaskStatus.APPROVED, Actor.owner(), "APPROVED", null);
        db.update("update workspace_tasks set approved_hash=?, approved_at=? where id=?", hash(row), Timestamp.from(Instant.now()), id);
        return task(accountId, id);
    }

    @Transactional
    public TaskView reject(String accountId, String id, int version, String reason) {
        return ownerMove(accountId, id, version, TaskStatus.REJECTED, "REJECTED", trim(reason, 1000));
    }

    @Transactional
    public TaskView accept(String accountId, String id, int version) {
        return ownerMove(accountId, id, version, TaskStatus.DONE, "ACCEPTED", null);
    }

    @Transactional
    public TaskView sendBack(String accountId, String id, int version, String note) {
        return ownerMove(accountId, id, version, TaskStatus.IN_PROGRESS, "SENT_BACK", trim(note, 1000));
    }

    /** The owner or the task's own role starts approved work. The approval must still match the text. */
    @Transactional
    public TaskView start(String accountId, Actor actor, String id) {
        requireOwner(accountId);
        Row row = load(id);
        if (!actor.isOwner()) requireOwnTask(actor, row, "workspace_start_task");
        if (row.status() == TaskStatus.IN_PROGRESS) return view(row, events(id));
        if (row.status() == TaskStatus.APPROVED && !hash(row).equals(row.approvedHash()))
            throw conflict("The task changed after it was approved. Ask the owner to approve it again.");
        move(row, TaskStatus.IN_PROGRESS, actor, "STARTED", null);
        return task(accountId, id);
    }

    /** Work is handed in for the owner's review, with evidence. Nobody can skip review. */
    @Transactional
    public TaskView submitEvidence(String accountId, Actor actor, String id, String evidence) {
        requireOwner(accountId);
        Row row = load(id);
        if (!actor.isOwner()) requireOwnTask(actor, row, "workspace_submit_evidence");
        String clean = trim(evidence, 4000);
        if (clean == null || clean.length() < 10)
            throw bad("Add the evidence: the pull request link and the test result lines that show it works.");
        if (row.status() == TaskStatus.IN_REVIEW && clean.equals(row.evidence())) return view(row, events(id));
        move(row, TaskStatus.IN_REVIEW, actor, "SUBMITTED", clean);
        db.update("update workspace_tasks set evidence=? where id=?", clean, id);
        return task(accountId, id);
    }

    // ---------------------------------------------------------------- helpers

    private TaskView ownerMove(String accountId, String id, int version, TaskStatus target, String action, String detail) {
        requireOwner(accountId);
        Row row = load(id);
        if (row.status() == target) return view(row, events(id));
        checkVersion(row, version);
        move(row, target, Actor.owner(), action, detail);
        return task(accountId, id);
    }

    private void move(Row row, TaskStatus target, Actor actor, String action, String detail) {
        if (!row.status().canMoveTo(target))
            throw conflict("A " + row.status().name().toLowerCase().replace('_', ' ') + " task cannot move to "
                    + target.name().toLowerCase().replace('_', ' ') + ".");
        int updated = db.update("update workspace_tasks set status=?, version=version+1, updated_at=? where id=? and version=?",
                target.name(), Timestamp.from(Instant.now()), row.id(), row.version());
        if (updated == 0) throw conflict("Someone changed this task a moment ago. Reload it and try again.");
        log(row.id(), actor, action, detail);
    }

    private void requireTool(Actor actor, String tool) {
        if (roles.find(actor.roleId()).isEmpty()) throw bad("Unknown role: " + actor.roleId() + ".");
        if (!roles.allows(actor.roleId(), tool))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The " + actor.roleId() + " role may not use " + tool + ".");
    }

    private void requireOwnTask(Actor actor, Row row, String tool) {
        requireTool(actor, tool);
        if (!row.roleId().equals(actor.roleId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This task belongs to the " + row.roleId() + " role.");
    }

    private void checkVersion(Row row, int version) {
        if (row.version() != version) throw conflict("Someone changed this task a moment ago. Reload it and try again.");
    }

    private TaskInput validate(TaskInput in) {
        if (in == null) throw bad("Send the task.");
        String title = trim(in.title(), 120);
        if (title == null) throw bad("Give the task a title.");
        String product = in.product() == null ? "" : in.product().strip().toUpperCase();
        if (!PRODUCTS.contains(product)) throw bad("Product must be one of " + String.join(", ", PRODUCTS.stream().sorted().toList()) + ".");
        String roleId = in.roleId() == null ? "" : in.roleId().strip();
        if (roles.find(roleId).isEmpty()) throw bad("Pick one of the roles: "
                + String.join(", ", roles.all().stream().map(AgentRoles.AgentRole::id).toList()) + ".");
        String objective = trim(in.objective(), 2000);
        if (objective == null) throw bad("Say what the task should achieve.");
        List<String> criteria = in.acceptanceCriteria() == null ? List.of()
                : in.acceptanceCriteria().stream().map(c -> trim(c == null ? null : c.replace('\n', ' '), 500))
                        .filter(c -> c != null).toList();
        if (criteria.isEmpty()) throw bad("Add at least one acceptance criterion.");
        if (criteria.size() > MAX_CRITERIA) throw bad("Keep it to " + MAX_CRITERIA + " acceptance criteria or split the task.");
        if (title.length() > 120) throw bad("Keep the title under 120 characters.");
        return new TaskInput(title, product, roleId, objective, criteria);
    }

    private static String trim(String text, int max) {
        if (text == null || text.isBlank()) return null;
        String t = text.strip();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private TaskView view(Row r, List<Event> events) {
        String roleName = roles.find(r.roleId()).map(AgentRoles.AgentRole::name).orElse(r.roleId());
        String handoff = r.status() == TaskStatus.APPROVED || r.status() == TaskStatus.IN_PROGRESS ? handoff(r, roleName) : null;
        return new TaskView(r.id(), r.title(), r.product(), r.roleId(), roleName, r.objective(), r.criteria(), r.status(),
                r.proposedBy(), r.approvedAt(), r.evidence(), r.version(), r.createdAt(), r.updatedAt(), handoff, events);
    }

    /** The text the owner pastes into Claude Code: the deepwater-developer feature command with the whole task. */
    static String handoff(Row r, String roleName) {
        StringBuilder out = new StringBuilder("/deepwater-developer:feature ").append(r.title()).append("\n\n")
                .append("Workspace task ").append(r.id()).append(" (").append(r.product()).append("), working as the ")
                .append(roleName).append(" agent (").append(r.roleId()).append(").\n")
                .append("Objective: ").append(r.objective()).append("\n")
                .append("Acceptance criteria:\n");
        for (String c : r.criteria()) out.append("- ").append(c).append("\n");
        out.append("\nBefore you change code, call the Deepwater MCP tool workspace_start_task with taskId ").append(r.id())
                .append(" and roleId ").append(r.roleId()).append(". When the pull request is open, call workspace_submit_evidence ")
                .append("with the pull request link and the test result lines. Do not merge or deploy.");
        return out.toString();
    }

    private Row load(String id) {
        var rows = db.query(SELECT + " where id=?", this::row, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such workspace task.");
        return rows.getFirst();
    }

    private List<Event> events(String taskId) {
        return db.query("select actor, action, detail, created_at from workspace_events where task_id=? order by created_at, id",
                (rs, n) -> new Event(rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant()), taskId);
    }

    private void log(String taskId, Actor actor, String action, String detail) {
        db.update("insert into workspace_events(id, task_id, actor, action, detail, created_at) values (?,?,?,?,?,?)",
                UUID.randomUUID().toString(), taskId, actor.name(), action, detail, Timestamp.from(Instant.now()));
    }

    private Row row(ResultSet rs, int n) throws SQLException {
        Timestamp approved = rs.getTimestamp("approved_at");
        return new Row(rs.getString("id"), rs.getString("title"), rs.getString("product"), rs.getString("role_id"),
                rs.getString("objective"), Arrays.stream(rs.getString("acceptance_criteria").split("\n")).toList(),
                TaskStatus.valueOf(rs.getString("status")), rs.getString("proposed_by"), rs.getString("approved_hash"),
                approved == null ? null : approved.toInstant(), rs.getString("evidence"), rs.getInt("version"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private static String hash(Row r) {
        return hash(new TaskInput(r.title(), r.product(), r.roleId(), r.objective(), r.criteria()));
    }

    private static String hash(TaskInput t) {
        List<String> parts = new ArrayList<>(List.of(t.title(), t.product(), t.roleId(), t.objective()));
        parts.addAll(t.acceptanceCriteria());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(String.join("\u0000", parts).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static TaskStatus parseStatus(String status) {
        try {
            return TaskStatus.valueOf(status.strip().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw bad("Unknown status: " + status + ".");
        }
    }

    private static ResponseStatusException bad(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private static ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}
