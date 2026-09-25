package com.deepwater.echo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Deterministic first-slice Echo learning flow; no model-generated grading. */
@Service
public class EchoService {
    private static final String LESSON_ID = "v2-time-first";
    private static final String SKILL_KEY = "danish.main-clause.v2";
    private static final Map<String, String> DIAGNOSTIC_KEYS = Map.of(
            "v2", "B", "definite", "C", "present", "B");
    private static final int[] REVIEW_INTERVALS = {1, 3, 7, 14};

    private final JdbcTemplate db;

    public EchoService(JdbcTemplate db) { this.db = db; }

    public EchoHome home(String accountId) {
        // Use a stable, non-account sentinel so SQL drivers never need to bind
        // an untyped NULL while serving public lesson content anonymously.
        String learnerId = accountId == null ? "__public__" : accountId;
        var profiles = db.query("select goal,self_reported_level,diagnostic_score,starting_route from echo_learner_profiles where account_id=?",
                (rs, row) -> new LearnerProfile(rs.getString("goal"), rs.getString("self_reported_level"),
                        rs.getInt("diagnostic_score"), rs.getString("starting_route")), learnerId);
        var lessons = db.query("select l.id,l.title,l.level,l.topic,l.objective,l.explanation,l.source_reference,l.content_status,l.skill_key," +
                        "s.mastery,s.next_review_at,s.attempts from echo_lessons l left join echo_skill_states s " +
                        "on s.account_id=? and s.skill_key=l.skill_key order by l.sort_order",
                (rs, row) -> lesson(learnerId, rs), learnerId);
        int attempts = db.queryForObject("select count(*) from echo_attempts where account_id=?", Integer.class, learnerId);
        int correct = db.queryForObject("select count(*) from echo_attempts where account_id=? and is_correct=true", Integer.class, learnerId);
        LearnerProfile profile = profiles.isEmpty() ? null : profiles.getFirst();
        String nextAction = profile == null ? "Set your learning goal and take a short starting check."
                : lessons.stream().anyMatch(LessonCard::due) ? "A review is due. Revisit the pattern before adding a new one."
                : attempts == 0 ? "Start the first Danish pattern lesson."
                : "Try the transfer prompt, then return when your review is due.";
        return new EchoHome(profile != null, profile, nextAction, lessons, attempts, correct);
    }

    private LessonCard lesson(String accountId, java.sql.ResultSet rs) throws java.sql.SQLException {
        String lessonId = rs.getString("id");
        String skill = rs.getString("skill_key");
        BigDecimal mastery = rs.getBigDecimal("mastery");
        java.sql.Timestamp dueTimestamp = rs.getTimestamp("next_review_at");
        Instant dueAt = dueTimestamp == null ? null : dueTimestamp.toInstant();
        int skillAttempts = rs.getInt("attempts");
        var exercises = db.query("select e.id,e.position,e.task_type,e.prompt," +
                        "(select a.is_correct from echo_attempts a where a.account_id=? and a.exercise_id=e.id " +
                        "order by a.created_at desc limit 1) as last_correct " +
                        "from echo_exercises e where e.lesson_id=? order by e.position",
                (exerciseRs, row) -> {
                    Boolean lastCorrect = (Boolean) exerciseRs.getObject("last_correct");
                    List<ExerciseOption> options = db.query("select option_key,option_text from echo_exercise_options where exercise_id=? order by sort_order",
                            (optionRs, optionRow) -> new ExerciseOption(optionRs.getString(1), optionRs.getString(2)), exerciseRs.getString("id"));
                    return new Exercise(exerciseRs.getString("id"), exerciseRs.getInt("position"),
                            exerciseRs.getString("task_type"), exerciseRs.getString("prompt"), options, lastCorrect);
                }, accountId, lessonId);
        boolean due = dueAt != null && !dueAt.isAfter(Instant.now());
        return new LessonCard(lessonId, rs.getString("title"), rs.getString("level"), rs.getString("topic"),
                rs.getString("objective"), rs.getString("explanation"), rs.getString("source_reference"),
                rs.getString("content_status"), mastery == null ? 0.0 : mastery.doubleValue(),
                skillAttempts, dueAt == null ? null : dueAt.toString(), due, exercises);
    }

    @Transactional
    public EchoHome start(String accountId, String goal, String selfReportedLevel, List<DiagnosticAnswer> answers) {
        if (goal == null || goal.isBlank() || goal.length() > 300)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Describe your Danish learning goal in 1–300 characters.");
        if (!List.of("new", "some", "comfortable").contains(selfReportedLevel))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose new, some experience, or comfortable.");
        int score = scoreDiagnostic(answers);
        String route = score <= 1 ? "GUIDED" : score == 2 ? "STANDARD" : "CHALLENGE";
        int exists = db.queryForObject("select count(*) from echo_learner_profiles where account_id=?", Integer.class, accountId);
        if (exists == 0) {
            db.update("insert into echo_learner_profiles(account_id,goal,self_reported_level,diagnostic_score,starting_route) values(?,?,?,?,?)",
                    accountId, goal.strip(), selfReportedLevel, score, route);
        } else {
            db.update("update echo_learner_profiles set goal=?,self_reported_level=?,diagnostic_score=?,starting_route=?,updated_at=current_timestamp where account_id=?",
                    goal.strip(), selfReportedLevel, score, route, accountId);
        }
        return home(accountId);
    }

    private int scoreDiagnostic(List<DiagnosticAnswer> answers) {
        if (answers == null || answers.size() != DIAGNOSTIC_KEYS.size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Answer all three starting-check questions.");
        var received = new java.util.HashMap<String, String>();
        for (DiagnosticAnswer answer : answers) {
            if (answer == null || answer.id() == null || answer.answer() == null || !DIAGNOSTIC_KEYS.containsKey(answer.id())
                    || received.put(answer.id(), answer.answer()) != null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The starting check contains an invalid or duplicate answer.");
        }
        if (received.size() != DIAGNOSTIC_KEYS.size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Answer all three starting-check questions.");
        return (int) DIAGNOSTIC_KEYS.entrySet().stream().filter(entry -> normalize(received.get(entry.getKey()))
                .equals(normalize(entry.getValue()))).count();
    }

    @Transactional
    public PracticeResult practice(String accountId, String exerciseId, String answer, Boolean transferSelfCheck) {
        if (answer == null || answer.isBlank() || answer.length() > 2000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter an answer of at most 2000 characters.");
        var rows = db.query("select e.task_type,e.expected_answer,l.skill_key,e.feedback_correct,e.feedback_incorrect " +
                        "from echo_exercises e join echo_lessons l on l.id=e.lesson_id where e.id=?",
                (rs, row) -> new ExerciseRule(rs.getString("task_type"), rs.getString("expected_answer"),
                        rs.getString("skill_key"), rs.getString("feedback_correct"), rs.getString("feedback_incorrect")), exerciseId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Learning exercise not found.");
        ExerciseRule rule = rows.getFirst();
        boolean transfer = "TRANSFER".equals(rule.taskType());
        if (transfer && transferSelfCheck == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Complete the self-check for your transfer answer.");
        boolean graded = !transfer;
        boolean correct = graded && normalize(answer).equals(normalize(rule.expectedAnswer()));
        String feedback = transfer
                ? "Saved as your own transfer example. Echo does not automatically score free writing; compare your sentence with the pattern and your self-check."
                : correct ? rule.feedbackCorrect() : rule.feedbackIncorrect();
        String storedAnswer = answer.strip();
        db.update("insert into echo_attempts(id,account_id,exercise_id,answer,is_correct,is_transfer) values(?,?,?,?,?,?)",
                UUID.randomUUID().toString(), accountId, exerciseId, storedAnswer, graded ? correct : null, transfer);

        Double mastery = null;
        Instant nextReview = null;
        if (graded) {
            SkillState previous = db.query("select mastery,correct_streak from echo_skill_states where account_id=? and skill_key=?",
                    (rs, row) -> new SkillState(rs.getDouble("mastery"), rs.getInt("correct_streak")), accountId, rule.skillKey())
                    .stream().findFirst().orElse(new SkillState(0.0, 0));
            double newMastery = Math.max(0.0, Math.min(1.0, previous.mastery() + (correct ? 0.2 : -0.2)));
            int streak = correct ? previous.correctStreak() + 1 : 0;
            int interval = correct ? REVIEW_INTERVALS[Math.min(streak - 1, REVIEW_INTERVALS.length - 1)] : 1;
            nextReview = Instant.now().plus(interval, ChronoUnit.DAYS);
            mastery = BigDecimal.valueOf(newMastery).setScale(4, RoundingMode.HALF_UP).doubleValue();
            int stateExists = db.queryForObject("select count(*) from echo_skill_states where account_id=? and skill_key=?",
                    Integer.class, accountId, rule.skillKey());
            if (stateExists == 0) {
                db.update("insert into echo_skill_states(account_id,skill_key,mastery,correct_streak,interval_days,next_review_at,attempts) values(?,?,?,?,?,?,1)",
                        accountId, rule.skillKey(), mastery, streak, interval, java.sql.Timestamp.from(nextReview));
            } else {
                db.update("update echo_skill_states set mastery=?,correct_streak=?,interval_days=?,next_review_at=?,attempts=attempts+1,updated_at=current_timestamp where account_id=? and skill_key=?",
                        mastery, streak, interval, java.sql.Timestamp.from(nextReview), accountId, rule.skillKey());
            }
        } else {
            mastery = currentMastery(accountId, rule.skillKey());
            nextReview = currentReview(accountId, rule.skillKey());
        }
        db.update("insert into audit_events(id,event_type,aggregate_id) values(?,?,?)",
                UUID.randomUUID().toString(), "ECHO_PRACTICE_SUBMITTED", accountId);
        return new PracticeResult(exerciseId, storedAnswer, graded, graded ? correct : null, feedback,
                mastery, nextReview == null ? null : nextReview.toString(), transfer);
    }

    private Double currentMastery(String accountId, String skillKey) {
        return db.query("select mastery from echo_skill_states where account_id=? and skill_key=?",
                (rs, row) -> rs.getDouble(1), accountId, skillKey).stream().findFirst().orElse(0.0);
    }

    private Instant currentReview(String accountId, String skillKey) {
        return db.query("select next_review_at from echo_skill_states where account_id=? and skill_key=?",
                (rs, row) -> rs.getTimestamp(1).toInstant(), accountId, skillKey).stream().findFirst().orElse(null);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String lower = Normalizer.normalize(value.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFKC);
        return lower.replaceAll("[\\p{Punct}\\s]+", " ").strip();
    }

    public record DiagnosticAnswer(String id, String answer) {}
    public record ExerciseOption(String key, String text) {}
    public record Exercise(String id, int position, String taskType, String prompt,
                           List<ExerciseOption> options, Boolean lastCorrect) {}
    public record LessonCard(String id, String title, String level, String topic, String objective,
                             String explanation, String sourceReference, String contentStatus,
                             double mastery, int attempts, String nextReviewAt, boolean due,
                             List<Exercise> exercises) {}
    public record LearnerProfile(String goal, String selfReportedLevel, int diagnosticScore, String startingRoute) {}
    public record EchoHome(boolean onboarded, LearnerProfile profile, String nextAction,
                           List<LessonCard> lessons, int totalAttempts, int correctAttempts) {}
    public record PracticeResult(String exerciseId, String answer, boolean graded, Boolean correct,
                                 String feedback, double mastery, String nextReviewAt, boolean transfer) {}
    private record ExerciseRule(String taskType, String expectedAnswer, String skillKey,
                                String feedbackCorrect, String feedbackIncorrect) {}
    private record SkillState(double mastery, int correctStreak) {}
}
