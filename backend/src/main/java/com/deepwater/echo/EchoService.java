package com.deepwater.echo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.text.Normalizer;
import java.time.LocalDate;
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
                        "on s.account_id=? and s.skill_key=l.skill_key where l.language_code=? and l.review_status='PUBLISHED' " +
                        "and (not exists(select 1 from echo_audio_lessons a where a.lesson_id=l.id) or exists(" +
                        "select 1 from echo_audio_lessons a where a.lesson_id=l.id and (a.rights_expires_at is null or a.rights_expires_at>=current_date))) " +
                        "order by l.sort_order",
                (rs, row) -> lesson(learnerId, rs), learnerId, languageCode);
        int attempts = db.queryForObject("select count(*) from echo_attempts where account_id=?", Integer.class, learnerId);
        int correct = db.queryForObject("select count(*) from echo_attempts where account_id=? and is_correct=true", Integer.class, learnerId);
        int audioAttempts = db.queryForObject("select count(*) from echo_audio_attempts where account_id=?", Integer.class, learnerId);
        LearnerProfile profile = profiles.isEmpty() ? null : profiles.getFirst();
        AudioRecommendation recommendation = chooseAudioRecommendation(lessons, profile);
        String nextAction = profile == null ? "Choose a language and set your learning goal."
                : lessons.stream().anyMatch(LessonCard::due) ? "A review is due. Revisit the pattern before adding a new one."
                : lessons.isEmpty() ? "No " + languageName(languageCode) + " lessons are published yet. Your teacher can add and approve the first lesson."
                : attempts == 0 ? "Start the first " + languageName(languageCode) + " pattern lesson."
                : "Try the transfer prompt, then return when your review is due.";
        return new EchoHome(profile != null, profile, nextAction, lessons, attempts, correct, audioAttempts,
                recommendation.lessonId(), recommendation.reason());
    }

    private AudioRecommendation chooseAudioRecommendation(List<LessonCard> lessons, LearnerProfile profile) {
        if (profile == null) return new AudioRecommendation(null, "Set a learning goal to get a level-aware audio suggestion.");
        List<LessonCard> audioLessons = lessons.stream().filter(lesson -> lesson.audio() != null).toList();
        if (audioLessons.isEmpty()) return new AudioRecommendation(null, "Your teacher has not published rights-cleared audio for this language yet.");
        String targetBand = switch (profile.selfReportedLevel()) {
            case "some" -> "A2";
            case "comfortable" -> "B1_B2";
            default -> "A0_A1";
        };
        java.util.Set<String> goalTerms = java.util.Arrays.stream(profile.goal().toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(term -> term.length() > 2).collect(java.util.stream.Collectors.toSet());
        LessonCard selected = null;
        int bestScore = Integer.MIN_VALUE;
        int bestTopicMatches = 0;
        for (LessonCard lesson : audioLessons) {
            String searchable = (lesson.title() + " " + lesson.topic() + " " + lesson.audio().topicTags()).toLowerCase(Locale.ROOT);
            int topicMatches = (int) goalTerms.stream().filter(searchable::contains).count();
            int score = (targetBand.equals(lesson.audio().editorialBand()) ? 100 : 0) + topicMatches;
            if (score > bestScore) { selected = lesson; bestScore = score; bestTopicMatches = topicMatches; }
        }
        String bandName = switch (selected.audio().editorialBand()) {
            case "A0_A1" -> "A0–A1 beginner";
            case "A2" -> "A2 elementary";
            case "B1_B2" -> "B1–B2 intermediate";
            default -> "C1–C2 advanced";
        };
        String reason = (targetBand.equals(selected.audio().editorialBand())
                ? "Suggested for your self-described starting route. "
                : "This is the closest available audio level to your self-described starting route. ")
                + "It is tagged " + selected.audio().topicTags() + " and is in the " + bandName
                + " band. Change the filters or choose another lesson whenever you prefer.";
        return new AudioRecommendation(selected.id(), reason);
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
                skillAttempts, dueAt == null ? null : dueAt.toString(), due, exercises, audioLesson(lessonId, false));
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
                            exerciseRs.getString("prompt"), exerciseRs.getString("expected_answer"), acceptedAnswers(exerciseId),
                            exerciseRs.getString("feedback_correct"), exerciseRs.getString("feedback_incorrect"), options);
                }, lessonId);
        return new TeacherLesson(lessonId, rs.getString("language_code"), rs.getString("title"),
                rs.getString("level"), rs.getString("topic"), rs.getString("objective"), rs.getString("explanation"),
                rs.getString("source_reference"), rs.getString("skill_key"), rs.getString("review_status"),
                rs.getInt("version"), rs.getString("revision_of"), exercises, audioLesson(lessonId, true));
    }

    private AudioLessonContent audioLesson(String lessonId, boolean teacherView) {
        var items = db.query("select media_type,source_title,creator,source_url,playback_url,editorial_band,topic_tags," +
                        "speech_rate,vocabulary_density,idiom_density,pronunciation_load,topic_familiarity," +
                        "before_listening_prompt,gist_prompt,focus_prompt,transcript_text,rights_basis,link_allowed," +
                        "playback_allowed,transcript_allowed,rights_reference,rights_expires_at " +
                        "from echo_audio_lessons where lesson_id=?" + (teacherView ? "" : " and link_allowed=true"),
                (rs, row) -> new AudioLessonContent(rs.getString("media_type"), rs.getString("source_title"),
                        rs.getString("creator"), rs.getString("source_url"), rs.getString("playback_url"),
                        rs.getString("editorial_band"), rs.getString("topic_tags"), rs.getInt("speech_rate"),
                        rs.getInt("vocabulary_density"), rs.getInt("idiom_density"), rs.getInt("pronunciation_load"),
                        rs.getInt("topic_familiarity"), rs.getString("before_listening_prompt"),
                        rs.getString("gist_prompt"), rs.getString("focus_prompt"),
                        rs.getBoolean("transcript_allowed"), rs.getString("transcript_text"),
                        rs.getString("rights_basis"), rs.getBoolean("link_allowed"), rs.getBoolean("playback_allowed"),
                        rs.getString("rights_reference"), rs.getDate("rights_expires_at") == null ? null : rs.getDate("rights_expires_at").toLocalDate().toString()),
                lessonId);
        if (items.isEmpty()) return null;
        AudioLessonContent item = items.getFirst();
        if (teacherView) return item;
        return new AudioLessonContent(item.mediaType(), item.sourceTitle(), item.creator(), item.sourceUrl(),
                item.playbackAllowed() ? item.playbackUrl() : null, item.editorialBand(), item.topicTags(),
                item.speechRate(), item.vocabularyDensity(), item.idiomDensity(), item.pronunciationLoad(),
                item.topicFamiliarity(), item.beforeListeningPrompt(), item.gistPrompt(), item.focusPrompt(),
                item.transcriptAllowed(), item.transcriptAllowed() ? item.transcriptText() : null,
                null, null, null, null, null);
    }

    @Transactional
    public TeacherLesson saveDraft(String teacherId, LessonDraft draft) {
        validateDraft(draft);
        String id = UUID.randomUUID().toString();
        int version = 1;
        int sortOrder;
        if (draft.revisionOf() != null && !draft.revisionOf().isBlank()) {
            var parent = db.query("select version,sort_order from echo_lessons where id=? and review_status in ('PUBLISHED','UNPUBLISHED') and language_code=?",
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
        if (draft.audio() != null) saveAudioDraft(id, draft.audio());
        int position = 0;
        for (ExerciseDraft exercise : draft.exercises()) {
            String exerciseId = id + "-" + (++position);
            String expectedAnswer = "TRANSFER".equals(exercise.taskType()) ? null : exercise.expectedAnswer();
            db.update("insert into echo_exercises(id,lesson_id,position,task_type,prompt,expected_answer,feedback_correct,feedback_incorrect) values(?,?,?,?,?,?,?,?)",
                    exerciseId, id, position, exercise.taskType(), exercise.prompt().strip(), expectedAnswer,
                    safe(exercise.feedbackCorrect()), safe(exercise.feedbackIncorrect()));
            saveAcceptedAnswers(db, exerciseId, exercise.acceptedAnswers());
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
        var rows = db.query("select revision_of,review_status from echo_lessons where id=? and review_status in ('DRAFT','UNPUBLISHED')",
                (rs, row) -> new PublishSource(rs.getString("revision_of"), LessonStatus.valueOf(rs.getString("review_status"))), lessonId);
        if (rows.isEmpty()) {
            int newer = db.queryForObject("select count(*) from echo_lessons where revision_of=? and review_status='PUBLISHED'", Integer.class, lessonId);
            if (newer > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "A newer revision of this lesson is published.");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft lesson not found.");
        }
        PublishSource source = rows.getFirst();
        validateAudioForPublication(lessonId);
        // Only a new revision replaces its parent; republishing a withdrawn lesson leaves its lineage alone.
        if (source.status() == LessonStatus.DRAFT && source.parentId() != null) {
            int archived = db.update("update echo_lessons set review_status='ARCHIVED' where id=? and review_status in ('PUBLISHED','UNPUBLISHED')", source.parentId());
            if (archived != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "This revision is no longer based on the current published lesson.");
        }
        int updated = db.update("update echo_lessons set review_status='PUBLISHED',content_status='Teacher-approved and published',approved_by=?,approved_at=current_timestamp where id=? and review_status=?",
                teacherId, lessonId, source.status().name());
        if (updated != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft lesson not found.");
        if (source.status() == LessonStatus.UNPUBLISHED) audit("ECHO_LESSON_REPUBLISHED", lessonId);
        return teacherLessons(teacherId).stream().filter(lesson -> lesson.id().equals(lessonId)).findFirst().orElseThrow();
    }

    /** Withdraws a published lesson from learners; attempts, mastery and review dates are left untouched. */
    @Transactional
    public TeacherLesson unpublish(String teacherId, String lessonId) {
        if (lessonId == null || lessonId.isBlank() || lessonId.length() > 120)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a lesson to unpublish.");
        int updated = db.update("update echo_lessons set review_status='UNPUBLISHED',content_status='Unpublished by teacher' where id=? and review_status='PUBLISHED'", lessonId);
        if (updated != 1) {
            int exists = db.queryForObject("select count(*) from echo_lessons where id=?", Integer.class, lessonId);
            if (exists == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Published lesson not found.");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a published lesson can be unpublished.");
        }
        audit("ECHO_LESSON_UNPUBLISHED", lessonId);
        return teacherLessons(teacherId).stream().filter(lesson -> lesson.id().equals(lessonId)).findFirst().orElseThrow();
    }

    private void audit(String eventType, String aggregateId) {
        db.update("insert into audit_events(id,event_type,aggregate_id) values(?,?,?)", UUID.randomUUID().toString(), eventType, aggregateId);
    }

    private record PublishSource(String parentId, LessonStatus status) {}

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
            if (exercise.acceptedAnswers() != null && (!"TEXT".equals(exercise.taskType()) || exercise.acceptedAnswers().size() > 10
                    || exercise.acceptedAnswers().stream().anyMatch(answer -> blank(answer, 500))))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only typed exercises take extra accepted answers: up to 10, each at most 500 characters.");
            if ("SELECT".equals(exercise.taskType())) {
                if (exercise.options() == null || exercise.options().size() < 2 || exercise.options().size() > 10
                        || exercise.options().stream().anyMatch(option -> option == null || blank(option.key(), 16) || blank(option.text(), 500))
                        || exercise.options().stream().noneMatch(option -> option.key().equals(exercise.expectedAnswer())))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choice exercises need 2–10 valid options including the expected-answer key.");
            }
        }
        if (draft.audio() != null) validateAudioDraft(draft.audio());
    }

    private void validateAudioDraft(AudioLessonDraft audio) {
        if (audio == null || !List.of("MUSIC", "PODCAST").contains(audio.mediaType())
                || blank(audio.sourceTitle(), 240) || blank(audio.creator(), 240)
                || !validHttpsUrl(audio.sourceUrl()) || blank(audio.editorialBand(), 16)
                || !List.of("A0_A1", "A2", "B1_B2", "C1_C2").contains(audio.editorialBand())
                || blank(audio.topicTags(), 500) || !demand(audio.speechRate()) || !demand(audio.vocabularyDensity())
                || !demand(audio.idiomDensity()) || !demand(audio.pronunciationLoad()) || !demand(audio.topicFamiliarity())
                || blank(audio.beforeListeningPrompt(), 1000) || blank(audio.gistPrompt(), 1000)
                || blank(audio.focusPrompt(), 1000) || blank(audio.rightsBasis(), 24)
                || !List.of("ORIGINAL", "LICENSED", "PROVIDER_TERMS", "UNVERIFIED").contains(audio.rightsBasis())
                || blank(audio.rightsReference(), 500) || audio.linkAllowed() == null
                || audio.playbackAllowed() == null || audio.transcriptAllowed() == null
                || (audio.playbackAllowed() && !validHttpsUrl(audio.playbackUrl()))
                || (!audio.playbackAllowed() && audio.playbackUrl() != null && !audio.playbackUrl().isBlank())
                || (audio.transcriptAllowed() && audio.transcriptText() != null && audio.transcriptText().length() > 5000)
                || (!audio.transcriptAllowed() && audio.transcriptText() != null && !audio.transcriptText().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Complete audio source, level, prompts, 1–5 demand ratings, and consistent rights permissions.");
        }
        if (audio.rightsExpiresAt() != null && !audio.rightsExpiresAt().isBlank()) {
            try { LocalDate.parse(audio.rightsExpiresAt()); }
            catch (java.time.format.DateTimeParseException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rights expiry must use YYYY-MM-DD.");
            }
        }
    }

    private boolean demand(Integer value) { return value != null && value >= 1 && value <= 5; }

    private boolean validHttpsUrl(String value) {
        if (blank(value, 1500)) return false;
        try {
            URI uri = URI.create(value.strip());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException ex) { return false; }
    }

    private void saveAudioDraft(String lessonId, AudioLessonDraft audio) {
        db.update("insert into echo_audio_lessons(lesson_id,media_type,source_title,creator,source_url,playback_url," +
                        "editorial_band,topic_tags,speech_rate,vocabulary_density,idiom_density,pronunciation_load," +
                        "topic_familiarity,before_listening_prompt,gist_prompt,focus_prompt,transcript_text,rights_basis," +
                        "link_allowed,playback_allowed,transcript_allowed,rights_reference,rights_expires_at) " +
                        "values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                lessonId, audio.mediaType(), audio.sourceTitle().strip(), audio.creator().strip(), audio.sourceUrl().strip(),
                audio.playbackUrl() == null || audio.playbackUrl().isBlank() ? null : audio.playbackUrl().strip(),
                audio.editorialBand(), audio.topicTags().strip(), audio.speechRate(),
                audio.vocabularyDensity(), audio.idiomDensity(), audio.pronunciationLoad(), audio.topicFamiliarity(),
                audio.beforeListeningPrompt().strip(), audio.gistPrompt().strip(), audio.focusPrompt().strip(),
                audio.transcriptAllowed() ? safe(audio.transcriptText()) : null, audio.rightsBasis(), audio.linkAllowed(),
                audio.playbackAllowed(), audio.transcriptAllowed(), audio.rightsReference().strip(),
                audio.rightsExpiresAt() == null || audio.rightsExpiresAt().isBlank() ? null : java.sql.Date.valueOf(audio.rightsExpiresAt()));
    }

    private void validateAudioForPublication(String lessonId) {
        var rows = db.query("select rights_basis,link_allowed,playback_url,playback_allowed,transcript_text," +
                        "transcript_allowed,rights_reference,rights_expires_at from echo_audio_lessons where lesson_id=?",
                (rs, row) -> new Object[]{rs.getString("rights_basis"), rs.getBoolean("link_allowed"),
                        rs.getString("playback_url"), rs.getBoolean("playback_allowed"), rs.getString("transcript_text"),
                        rs.getBoolean("transcript_allowed"), rs.getString("rights_reference"), rs.getDate("rights_expires_at")}, lessonId);
        if (rows.isEmpty()) return;
        Object[] rights = rows.getFirst();
        java.sql.Date expiresAt = (java.sql.Date) rights[7];
        if ("UNVERIFIED".equals(rights[0]) || !Boolean.TRUE.equals(rights[1])
                || blank((String) rights[6], 500)
                || (Boolean.TRUE.equals(rights[3]) && blank((String) rights[2], 1500))
                || (Boolean.FALSE.equals(rights[3]) && rights[2] != null)
                || (Boolean.FALSE.equals(rights[5]) && rights[4] != null)
                || (expiresAt != null && expiresAt.toLocalDate().isBefore(LocalDate.now()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Audio lesson cannot publish: verify source-link rights, remove unauthorized media/transcript, and check rights expiry.");
        }
    }
    private boolean blank(String value, int max) { return value == null || value.isBlank() || value.length() > max; }
    private String safe(String value) { return value == null ? "" : value.strip(); }

    @Transactional
    public PracticeResult practice(String accountId, String exerciseId, String answer, Boolean transferSelfCheck) {
        if (answer == null || answer.isBlank() || answer.length() > 2000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter an answer of at most 2000 characters.");
        var rows = db.query("select e.task_type,e.expected_answer,l.skill_key,e.feedback_correct,e.feedback_incorrect " +
                        "from echo_exercises e join echo_lessons l on l.id=e.lesson_id where e.id=? and l.review_status='PUBLISHED'",
                (rs, row) -> new ExerciseRule(rs.getString("task_type"), rs.getString("expected_answer"),
                        rs.getString("skill_key"), rs.getString("feedback_correct"), rs.getString("feedback_incorrect")), exerciseId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Learning exercise not found.");
        ExerciseRule rule = rows.getFirst();
        boolean transfer = "TRANSFER".equals(rule.taskType());
        if (transfer && transferSelfCheck == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Complete the self-check for your transfer answer.");
        boolean graded = !transfer;
        List<String> accepted = new java.util.ArrayList<>();
        if (graded) {
            accepted.add(rule.expectedAnswer());
            if ("TEXT".equals(rule.taskType())) accepted.addAll(acceptedAnswers(exerciseId));
        }
        boolean correct = graded && accepted.stream().anyMatch(expected -> normalize(answer).equals(normalize(expected)));
        String feedback = transfer
                ? "Saved as your own transfer example. Echo does not automatically score free writing; compare your sentence with the pattern and your self-check."
                : correct ? rule.feedbackCorrect() : rule.feedbackIncorrect();
        String hint = graded && !correct && "TEXT".equals(rule.taskType()) ? AnswerHints.nearMiss(answer, accepted) : null;
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
        return new PracticeResult(exerciseId, storedAnswer, graded, graded ? correct : null, feedback, hint,
                mastery, nextReview == null ? null : nextReview.toString(), transfer);
    }

    @Transactional
    public AudioPracticeResult audioEvidence(String accountId, String lessonId, String taskType,
                                             String answer, boolean transcriptUsed, int confidence) {
        if (answer == null || answer.isBlank() || answer.length() > 2000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a short reflection of at most 2000 characters.");
        if (!List.of("GIST", "PHRASE", "STRUCTURE", "SOUND", "TRANSFER").contains(taskType))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a supported audio learning step.");
        if (confidence < 1 || confidence > 5)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confidence must be between 1 and 5.");
        var active = db.query("select a.transcript_allowed from echo_audio_lessons a join echo_lessons l on l.id=a.lesson_id " +
                        "where a.lesson_id=? and l.review_status='PUBLISHED' and a.link_allowed=true " +
                        "and (a.rights_expires_at is null or a.rights_expires_at>=current_date)",
                (rs, row) -> rs.getBoolean(1), lessonId);
        if (active.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Published audio lesson not found.");
        if (transcriptUsed && !active.getFirst())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This lesson does not have permission to display a transcript.");
        db.update("insert into echo_audio_attempts(id,account_id,lesson_id,task_type,response_chars,transcript_used,confidence) values(?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), accountId, lessonId, taskType, answer.strip().length(), transcriptUsed, confidence);
        db.update("insert into audit_events(id,event_type,aggregate_id) values(?,?,?)",
                UUID.randomUUID().toString(), "ECHO_AUDIO_EVIDENCE_SAVED", accountId);
        boolean repeatGist = "GIST".equals(taskType) && confidence <= 2;
        return new AudioPracticeResult(true, taskType, transcriptUsed, confidence,
                repeatGist ? "Saved. Your confidence was low, so try the first listen once more; use the context prompt as a clue before moving on."
                        : "Saved as listening evidence. Echo records this reflection without automatically scoring your listening or grammar.",
                repeatGist ? "GIST" : nextAudioTask(taskType), repeatGist);
    }

    private String nextAudioTask(String taskType) {
        return switch (taskType) {
            case "GIST" -> "PHRASE";
            case "PHRASE" -> "STRUCTURE";
            case "STRUCTURE" -> "TRANSFER";
            default -> "DONE";
        };
    }

    private Double currentMastery(String accountId, String skillKey) {
        return db.query("select mastery from echo_skill_states where account_id=? and skill_key=?",
                (rs, row) -> rs.getDouble(1), accountId, skillKey).stream().findFirst().orElse(0.0);
    }

    private Instant currentReview(String accountId, String skillKey) {
        return db.query("select next_review_at from echo_skill_states where account_id=? and skill_key=?",
                (rs, row) -> rs.getTimestamp(1).toInstant(), accountId, skillKey).stream().findFirst().orElse(null);
    }

    private List<String> acceptedAnswers(String exerciseId) {
        return db.queryForList("select answer_text from echo_exercise_answers where exercise_id=? order by sort_order", String.class, exerciseId);
    }

    /** Stores alternative correct answers for a typed exercise; the expected answer stays the primary one. */
    public static void saveAcceptedAnswers(JdbcTemplate db, String exerciseId, List<String> answers) {
        if (answers == null) return;
        int position = 0;
        for (String answer : answers)
            db.update("insert into echo_exercise_answers(exercise_id,answer_text,sort_order) values(?,?,?)", exerciseId, answer.strip(), ++position);
    }

    static String normalize(String value) {
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
                             List<Exercise> exercises, AudioLessonContent audio) {}
    public record LearnerProfile(String goal, String selfReportedLevel, String languageCode, int diagnosticScore, String startingRoute) {}
    public record LessonDraft(String revisionOf, String languageCode, String title, String level, String topic, String objective,
                              String explanation, String sourceReference, String skillKey, Integer sortOrder,
                              List<ExerciseDraft> exercises, AudioLessonDraft audio) {}
    public record AudioLessonDraft(String mediaType, String sourceTitle, String creator, String sourceUrl,
                                   String playbackUrl, String editorialBand, String topicTags,
                                   Integer speechRate, Integer vocabularyDensity, Integer idiomDensity,
                                   Integer pronunciationLoad, Integer topicFamiliarity, String beforeListeningPrompt,
                                   String gistPrompt, String focusPrompt, String transcriptText, String rightsBasis,
                                   Boolean linkAllowed, Boolean playbackAllowed, Boolean transcriptAllowed,
                                   String rightsReference, String rightsExpiresAt) {}
    public record AudioLessonContent(String mediaType, String sourceTitle, String creator, String sourceUrl,
                                     String playbackUrl, String editorialBand, String topicTags,
                                     int speechRate, int vocabularyDensity, int idiomDensity, int pronunciationLoad,
                                     int topicFamiliarity, String beforeListeningPrompt, String gistPrompt,
                                     String focusPrompt, boolean transcriptAllowed, String transcriptText,
                                     String rightsBasis, Boolean linkAllowed, Boolean playbackAllowed,
                                     String rightsReference, String rightsExpiresAt) {}
    public record ExerciseDraft(String taskType, String prompt, String expectedAnswer, List<String> acceptedAnswers,
                                String feedbackCorrect, String feedbackIncorrect, List<ExerciseOption> options) {}
    public record TeacherExercise(int position, String taskType, String prompt, String expectedAnswer, List<String> acceptedAnswers,
                                  String feedbackCorrect, String feedbackIncorrect, List<ExerciseOption> options) {}
    public record TeacherLesson(String id, String languageCode, String title, String level, String topic, String objective,
                                String explanation, String sourceReference, String skillKey, String reviewStatus, int version,
                                String revisionOf, List<TeacherExercise> exercises, AudioLessonContent audio) {}
    private record LessonOrder(int version, int sortOrder) {}
    public record EchoHome(boolean onboarded, LearnerProfile profile, String nextAction,
                           List<LessonCard> lessons, int totalAttempts, int correctAttempts, int audioAttempts,
                           String recommendedAudioLessonId, String recommendedAudioReason) {}
    public record AudioRecommendation(String lessonId, String reason) {}
    public record AudioPracticeResult(boolean saved, String taskType, boolean transcriptUsed,
                                      int confidence, String feedback, String nextTaskType, boolean repeatSuggested) {}
    public record PracticeResult(String exerciseId, String answer, boolean graded, Boolean correct,
                                 String feedback, String hint, double mastery, String nextReviewAt, boolean transfer) {}
    private record ExerciseRule(String taskType, String expectedAnswer, String skillKey,
                                String feedbackCorrect, String feedbackIncorrect) {}
    private record SkillState(double mastery, int correctStreak) {}
}
