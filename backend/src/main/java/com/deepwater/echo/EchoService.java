package com.deepwater.echo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Deterministic first-slice Echo learning flow; no model-generated grading. */
@Service
public class EchoService {
    private static final int[] REVIEW_INTERVALS = {1, 3, 7, 14};

    private final JdbcTemplate db;

    public EchoService(JdbcTemplate db) { this.db = db; }

    public EchoHome home(String accountId) {
        // Use a stable, non-account sentinel so SQL drivers never need to bind
        // an untyped NULL while serving public lesson content anonymously.
        String learnerId = accountId == null ? "__public__" : accountId;
        var profiles = db.query("select goal,self_reported_level,diagnostic_score,starting_route,language_code from echo_learner_profiles where account_id=?",
                (rs, row) -> new LearnerProfile(rs.getString("goal"), rs.getString("self_reported_level"),
                        rs.getString("language_code"),
                        rs.getInt("diagnostic_score"), rs.getString("starting_route")), learnerId);
        String languageCode = profiles.isEmpty() ? "da" : profiles.getFirst().languageCode();
        var lessons = db.query("select l.id,l.title,l.level,l.topic,l.objective,l.explanation,l.source_reference,l.content_status,l.skill_key," +
                        "s.mastery,s.next_review_at,s.attempts from echo_lessons l left join echo_skill_states s " +
                        "on s.account_id=? and s.skill_key=l.skill_key where l.language_code=? and l.review_status='PUBLISHED' order by l.sort_order",
                (rs, row) -> lesson(learnerId, rs), learnerId, languageCode);
        int attempts = db.queryForObject("select count(*) from echo_attempts where account_id=?", Integer.class, learnerId);
        int correct = db.queryForObject("select count(*) from echo_attempts where account_id=? and is_correct=true", Integer.class, learnerId);
        LearnerProfile profile = profiles.isEmpty() ? null : profiles.getFirst();
        String nextAction = profile == null ? "Choose a language and set your learning goal."
                : lessons.stream().anyMatch(LessonCard::due) ? "A review is due. Revisit the pattern before adding a new one."
                : lessons.isEmpty() ? "No " + languageName(languageCode) + " lessons are published yet. Your teacher can add and approve the first lesson."
                : attempts == 0 ? "Start the first " + languageName(languageCode) + " pattern lesson."
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
    public EchoHome start(String accountId, String goal, String languageCode, String selfReportedLevel) {
        if (!"da".equals(languageCode) && !"de".equals(languageCode))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose Danish or German.");
        if (goal == null || goal.isBlank() || goal.length() > 300)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Describe your language learning goal in 1–300 characters.");
        if (selfReportedLevel == null || !List.of("new", "some", "comfortable").contains(selfReportedLevel))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose new, some experience, or comfortable.");
        int score = 0; // Legacy database column retained for existing profiles; no quiz score is shown.
        String route = switch (selfReportedLevel) {
            case "some" -> "STANDARD";
            case "comfortable" -> "CHALLENGE";
            default -> "GUIDED";
        };
        int exists = db.queryForObject("select count(*) from echo_learner_profiles where account_id=?", Integer.class, accountId);
        if (exists == 0) {
            db.update("insert into echo_learner_profiles(account_id,goal,self_reported_level,diagnostic_score,starting_route,language_code) values(?,?,?,?,?,?)",
                    accountId, goal.strip(), selfReportedLevel, score, route, languageCode);
        } else {
            db.update("update echo_learner_profiles set goal=?,self_reported_level=?,diagnostic_score=?,starting_route=?,language_code=?,updated_at=current_timestamp where account_id=?",
                    goal.strip(), selfReportedLevel, score, route, languageCode, accountId);
        }
        return home(accountId);
    }

    @Transactional
    public EchoHome chooseLanguage(String accountId, String languageCode) {
        if (!"da".equals(languageCode) && !"de".equals(languageCode))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose Danish or German.");
        int updated = db.update("update echo_learner_profiles set language_code=?,updated_at=current_timestamp where account_id=?",
                languageCode, accountId);
        if (updated != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Set up your Echo profile before changing language.");
        return home(accountId);
    }

    private String languageName(String code) { return "de".equals(code) ? "German" : "Danish"; }

    @Transactional
    public List<TeacherLesson> teacherLessons(String teacherId) {
        return db.query("select id,language_code,title,level,topic,objective,explanation,source_reference,skill_key,review_status,version,revision_of from echo_lessons order by language_code,sort_order,version desc",
                (rs, row) -> teacherLesson(rs));
    }

    private TeacherLesson teacherLesson(java.sql.ResultSet rs) throws java.sql.SQLException {
        String lessonId = rs.getString("id");
        List<TeacherExercise> exercises = db.query("select id,position,task_type,prompt,expected_answer,feedback_correct,feedback_incorrect from echo_exercises where lesson_id=? order by position",
                (exerciseRs, row) -> {
                    String exerciseId = exerciseRs.getString("id");
                    List<ExerciseOption> options = db.query("select option_key,option_text from echo_exercise_options where exercise_id=? order by sort_order",
                            (optionRs, optionRow) -> new ExerciseOption(optionRs.getString(1), optionRs.getString(2)), exerciseId);
                    return new TeacherExercise(exerciseRs.getInt("position"), exerciseRs.getString("task_type"),
                            exerciseRs.getString("prompt"), exerciseRs.getString("expected_answer"),
                            exerciseRs.getString("feedback_correct"), exerciseRs.getString("feedback_incorrect"), options);
                }, lessonId);
        return new TeacherLesson(lessonId, rs.getString("language_code"), rs.getString("title"),
                rs.getString("level"), rs.getString("topic"), rs.getString("objective"), rs.getString("explanation"),
                rs.getString("source_reference"), rs.getString("skill_key"), rs.getString("review_status"),
                rs.getInt("version"), rs.getString("revision_of"), exercises);
    }

    @Transactional
    public TeacherLesson saveDraft(String teacherId, LessonDraft draft) {
        validateDraft(draft);
        String id = UUID.randomUUID().toString();
        int version = 1;
        int sortOrder;
        if (draft.revisionOf() != null && !draft.revisionOf().isBlank()) {
            var parent = db.query("select version,sort_order from echo_lessons where id=? and review_status='PUBLISHED' and language_code=?",
                    (rs, row) -> new LessonOrder(rs.getInt("version"), rs.getInt("sort_order")), draft.revisionOf(), draft.languageCode())
                    .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "The lesson being revised was not found."));
            version = parent.version() + 1;
            sortOrder = parent.sortOrder();
        } else {
            Integer maxOrder = db.queryForObject("select coalesce(max(sort_order),0) from echo_lessons where language_code=?", Integer.class, draft.languageCode());
            sortOrder = draft.sortOrder() == null ? maxOrder + 1 : draft.sortOrder();
        }
        if (sortOrder < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lesson order must be a positive number.");
        try {
            db.update("insert into echo_lessons(id,language_code,title,level,topic,objective,explanation,source_reference,content_status,skill_key,sort_order,review_status,version,revision_of,created_by) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, draft.languageCode(), draft.title().strip(), draft.level().strip(), draft.topic().strip(), draft.objective().strip(),
                draft.explanation().strip(), draft.sourceReference().strip(), "Teacher draft", draft.skillKey().strip(),
                sortOrder, "DRAFT", version, draft.revisionOf(), teacherId);
        int position = 0;
        for (ExerciseDraft exercise : draft.exercises()) {
            String exerciseId = id + "-" + (++position);
            String expectedAnswer = "TRANSFER".equals(exercise.taskType()) ? null : exercise.expectedAnswer();
            db.update("insert into echo_exercises(id,lesson_id,position,task_type,prompt,expected_answer,feedback_correct,feedback_incorrect) values(?,?,?,?,?,?,?,?)",
                    exerciseId, id, position, exercise.taskType(), exercise.prompt().strip(), expectedAnswer,
                    safe(exercise.feedbackCorrect()), safe(exercise.feedbackIncorrect()));
            if (exercise.options() != null) {
                int optionPosition = 0;
                for (ExerciseOption option : exercise.options()) {
                    if (option.key() == null || option.key().isBlank() || option.key().length() > 16 || option.text() == null || option.text().isBlank() || option.text().length() > 500)
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exercise options need a short key and text.");
                    db.update("insert into echo_exercise_options(exercise_id,option_key,option_text,sort_order) values(?,?,?,?)",
                            exerciseId, option.key(), option.text().strip(), ++optionPosition);
                }
            }
        }
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This lesson draft conflicts with existing content.");
        }
        return teacherLessons(teacherId).stream().filter(lesson -> lesson.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    public TeacherLesson publish(String teacherId, String lessonId) {
        var revisionRows = db.query("select revision_of from echo_lessons where id=? and review_status='DRAFT'", (rs, row) -> rs.getString(1), lessonId);
        if (revisionRows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft lesson not found.");
        String parentId = revisionRows.getFirst();
        if (parentId != null) {
            int archived = db.update("update echo_lessons set review_status='ARCHIVED' where id=? and review_status='PUBLISHED'", parentId);
            if (archived != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "This revision is no longer based on the current published lesson.");
        }
        int updated = db.update("update echo_lessons set review_status='PUBLISHED',content_status='Teacher-approved and published',approved_by=?,approved_at=current_timestamp where id=? and review_status='DRAFT'",
                teacherId, lessonId);
        if (updated != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft lesson not found.");
        return teacherLessons(teacherId).stream().filter(lesson -> lesson.id().equals(lessonId)).findFirst().orElseThrow();
    }

    private void validateDraft(LessonDraft draft) {
        if (draft == null || (!"da".equals(draft.languageCode()) && !"de".equals(draft.languageCode())) || blank(draft.title(), 240)
                || blank(draft.level(), 32) || blank(draft.topic(), 120) || blank(draft.objective(), 500)
                || blank(draft.explanation(), 3000) || blank(draft.sourceReference(), 500)
                || blank(draft.skillKey(), 120) || draft.exercises() == null || draft.exercises().isEmpty() || draft.exercises().size() > 20)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Complete lesson details, source reference, and 1–20 exercises.");
        for (ExerciseDraft exercise : draft.exercises()) {
            if (exercise == null || exercise.taskType() == null || !List.of("SELECT", "TEXT", "TRANSFER").contains(exercise.taskType()) || blank(exercise.prompt(), 1000))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each exercise needs a supported type and prompt.");
            if (exercise.taskType().equals("TRANSFER") && exercise.expectedAnswer() != null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transfer exercises must not define an automated expected answer.");
            if (!"TRANSFER".equals(exercise.taskType()) && blank(exercise.expectedAnswer(), 500))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Graded exercises need an expected answer.");
            if ("SELECT".equals(exercise.taskType())) {
                if (exercise.options() == null || exercise.options().size() < 2 || exercise.options().size() > 10
                        || exercise.options().stream().anyMatch(option -> option == null || blank(option.key(), 16) || blank(option.text(), 500))
                        || exercise.options().stream().noneMatch(option -> option.key().equals(exercise.expectedAnswer())))
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choice exercises need 2–10 valid options including the expected-answer key.");
            }
        }
    }
    private boolean blank(String value, int max) { return value == null || value.isBlank() || value.length() > max; }
    private String safe(String value) { return value == null ? "" : value.strip(); }

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

    public record ExerciseOption(String key, String text) {}
    public record Exercise(String id, int position, String taskType, String prompt,
                           List<ExerciseOption> options, Boolean lastCorrect) {}
    public record LessonCard(String id, String title, String level, String topic, String objective,
                             String explanation, String sourceReference, String contentStatus,
                             double mastery, int attempts, String nextReviewAt, boolean due,
                             List<Exercise> exercises) {}
    public record LearnerProfile(String goal, String selfReportedLevel, String languageCode, int diagnosticScore, String startingRoute) {}
    public record LessonDraft(String revisionOf, String languageCode, String title, String level, String topic, String objective,
                              String explanation, String sourceReference, String skillKey, Integer sortOrder, List<ExerciseDraft> exercises) {}
    public record ExerciseDraft(String taskType, String prompt, String expectedAnswer, String feedbackCorrect,
                                String feedbackIncorrect, List<ExerciseOption> options) {}
    public record TeacherExercise(int position, String taskType, String prompt, String expectedAnswer,
                                  String feedbackCorrect, String feedbackIncorrect, List<ExerciseOption> options) {}
    public record TeacherLesson(String id, String languageCode, String title, String level, String topic, String objective,
                                String explanation, String sourceReference, String skillKey, String reviewStatus, int version,
                                String revisionOf, List<TeacherExercise> exercises) {}
    private record LessonOrder(int version, int sortOrder) {}
    public record EchoHome(boolean onboarded, LearnerProfile profile, String nextAction,
                           List<LessonCard> lessons, int totalAttempts, int correctAttempts) {}
    public record PracticeResult(String exerciseId, String answer, boolean graded, Boolean correct,
                                 String feedback, double mastery, String nextReviewAt, boolean transfer) {}
    private record ExerciseRule(String taskType, String expectedAnswer, String skillKey,
                                String feedbackCorrect, String feedbackIncorrect) {}
    private record SkillState(double mastery, int correctStreak) {}
}
