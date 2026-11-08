package com.deepwater.exam;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/**
 * Marks a Polish B1 practice attempt. Closed parts (listening, grammar, reading) are marked against the
 * answer key; writing and speaking go to the {@link WritingAssessor}. Every part must reach the pass mark
 * for the learner to look ready for B1. The result is an estimate for practice, never an official grade.
 */
@Service
public class ExamService {
    private static final Logger LOG = LoggerFactory.getLogger(ExamService.class);
    private static final int MAX_ANSWER = 4000;
    private final ExamContent content;
    private final WritingAssessor assessor;
    private final WritingAssessor rules = new RuleWritingAssessor();
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final int passPercent;

    public ExamService(ExamContent content, WritingAssessor assessor, JdbcTemplate db, ObjectMapper json,
                       @Value("${deepwater.exam.pass-percent:60}") int passPercent) {
        this.content = content;
        this.assessor = assessor;
        this.db = db;
        this.json = json;
        this.passPercent = Math.clamp(passPercent, 1, 100);
    }

    public boolean aiExaminer() { return !(assessor instanceof RuleWritingAssessor); }

    public int passPercent() { return passPercent; }

    public Result submit(String accountId, Map<String, String> answers) {
        Map<String, String> given = answers == null ? Map.of() : answers;
        for (String value : given.values())
            if (value != null && value.length() > MAX_ANSWER)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keep each answer under " + MAX_ANSWER + " characters.");
        ExamContent.Exam exam = content.exam();
        List<WritingAssessor.OpenAnswer> open = new ArrayList<>();
        for (ExamContent.Part part : exam.parts())
            if (!part.closed())
                for (ExamContent.Item item : part.items())
                    open.add(new WritingAssessor.OpenAnswer(item.id(), part.kind(), item.prompt(), item.minWords(), item.maxWords(),
                            item.topicWords(), given.getOrDefault(item.id(), "")));
        WritingAssessor.Review review = review(open);

        List<PartResult> parts = new ArrayList<>();
        for (ExamContent.Part part : exam.parts()) parts.add(part.closed() ? closedPart(part, given) : openPart(part, review));
        boolean ready = parts.stream().allMatch(PartResult::passed);
        int average = (int) Math.round(parts.stream().mapToInt(PartResult::percent).average().orElse(0));
        String level = levelEstimate(parts, average);
        List<String> weak = parts.stream().filter(p -> !p.passed()).map(PartResult::english).toList();
        String verdict = ready
                ? "You look ready for the B1 exam: every part reached " + passPercent + "%. This is a practice estimate, not an official result."
                : "Not ready for B1 yet. Work on: " + String.join(", ", weak) + ". Each part needs " + passPercent + "% or more.";
        Result result = new Result(UUID.randomUUID().toString(), exam.id(), Instant.now(), parts, average, passPercent, ready, level,
                review.overallLevel(), review.overallSummary(), verdict, review.markedBy());
        db.update("insert into exam_attempts(id,account_id,exam_id,eligible,level_estimate,average_percent,result) values(?,?,?,?,?,?,?)",
                result.id(), accountId, exam.id(), ready, level, average, json.writeValueAsString(result));
        return result;
    }

    public List<AttemptSummary> history(String accountId) {
        return db.query("select id, created_at, eligible, level_estimate, average_percent from exam_attempts where account_id=? "
                        + "order by created_at desc limit 20",
                (rs, row) -> new AttemptSummary(rs.getString("id"), instant(rs.getTimestamp("created_at")), rs.getBoolean("eligible"),
                        rs.getString("level_estimate"), rs.getInt("average_percent")), accountId);
    }

    public Result attempt(String accountId, String id) {
        List<String> rows = db.queryForList("select result from exam_attempts where account_id=? and id=?", String.class, accountId, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found.");
        return json.readValue(rows.getFirst(), Result.class);
    }

    private WritingAssessor.Review review(List<WritingAssessor.OpenAnswer> open) {
        WritingAssessor.Review primary;
        try {
            primary = assessor.review(open);
        } catch (RuntimeException ex) {
            LOG.warn("AI examiner unavailable, marking with the fixed rubric: {}", ex.getMessage());
            return rules.review(open);
        }
        // Anything the AI did not mark is marked by the rules, so every answer gets a mark.
        List<WritingAssessor.OpenAnswer> missing = open.stream().filter(a -> !primary.items().containsKey(a.itemId())).toList();
        if (missing.isEmpty()) return primary;
        Map<String, WritingAssessor.ItemAssessment> items = new LinkedHashMap<>(primary.items());
        items.putAll(rules.review(missing).items());
        return new WritingAssessor.Review(items, primary.overallLevel(), primary.overallSummary(), primary.markedBy());
    }

    private PartResult closedPart(ExamContent.Part part, Map<String, String> given) {
        List<ItemResult> items = new ArrayList<>();
        int correct = 0;
        for (ExamContent.Item item : part.items()) {
            Integer chosen = choice(given.get(item.id()));
            boolean right = chosen != null && chosen.equals(item.answer());
            if (right) correct++;
            items.add(new ItemResult(item.id(), right ? 100 : 0, chosen, item.answer(), null));
        }
        int percent = (int) Math.round(100.0 * correct / part.items().size());
        return new PartResult(part.id(), part.name(), part.english(), percent, percent >= passPercent, items);
    }

    private PartResult openPart(ExamContent.Part part, WritingAssessor.Review review) {
        List<ItemResult> items = part.items().stream()
                .map(item -> {
                    WritingAssessor.ItemAssessment a = review.items().get(item.id());
                    return new ItemResult(item.id(), a == null ? 0 : a.score(), null, null, a);
                }).toList();
        int percent = (int) Math.round(items.stream().mapToInt(ItemResult::score).average().orElse(0));
        return new PartResult(part.id(), part.name(), part.english(), percent, percent >= passPercent, items);
    }

    private String levelEstimate(List<PartResult> parts, int average) {
        boolean allPass = parts.stream().allMatch(PartResult::passed);
        if (allPass && average >= 85) return "B1+";
        if (allPass) return "B1";
        if (average >= passPercent - 20) return "A2";
        return "A1-A2";
    }

    private static Integer choice(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Integer.valueOf(value.strip()); } catch (NumberFormatException _) { return null; }
    }

    private static Instant instant(Timestamp t) { return t == null ? null : t.toInstant(); }

    public record Result(String id, String examId, Instant createdAt, List<PartResult> parts, int averagePercent, int passPercent,
                         boolean readyForB1, String levelEstimate, String examinerLevel, String examinerSummary, String verdict,
                         String markedBy) {}

    public record PartResult(String id, String name, String english, int percent, boolean passed, List<ItemResult> items) {}

    /** chosen and answer are option indexes for closed items; assessment is set for open items. */
    public record ItemResult(String id, int score, Integer chosen, Integer answer, WritingAssessor.ItemAssessment assessment) {}

    public record AttemptSummary(String id, Instant createdAt, boolean readyForB1, String levelEstimate, int averagePercent) {}
}
