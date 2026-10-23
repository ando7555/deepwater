package com.deepwater;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** Ticket #106. Own in-memory database so lessons published here never reach the shared one. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:unpublish-lesson;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
                "deepwater.teacher.email=unpublish-teacher@deepwater.test"})
@AutoConfigureTestRestTemplate
class UnpublishLessonTest {
    private static final String PUBLISH = "mutation($id:ID!){publishEchoLesson(lessonId:$id){reviewStatus}}";
    private static final String UNPUBLISH = "mutation($id:ID!){unpublishEchoLesson(lessonId:$id){id reviewStatus exercises{prompt}}}";
    private static final String PRACTICE = "mutation($id:ID!,$a:String!){submitEchoPractice(exerciseId:$id,answer:$a){correct mastery}}";

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate db;

    @Test
    void unpublishHidesLessonFromLearnersKeepsProgressAndRepublishRestoresIt() {
        String teacher = teacherToken();
        String learner = register("learner");
        String lessonId = publishedLesson(teacher, "Unpublish me", "de", null);
        graph("mutation{startEcho(goal:\"Use German at work\",languageCode:\"de\",selfReportedLevel:\"new\"){onboarded}}", Map.of(), learner);
        assertThat(graph(PRACTICE, Map.of("id", lessonId + "-1", "a", "A"), learner)).contains("\"mastery\":0.2");

        String result = graph(UNPUBLISH, Map.of("id", lessonId), teacher);
        assertThat(result).contains("\"reviewStatus\":\"UNPUBLISHED\"", "Choose the order.");
        assertThat(status(lessonId)).isEqualTo("UNPUBLISHED");
        assertThat(db.queryForObject("select content_status from echo_lessons where id=?", String.class, lessonId)).isEqualTo("Unpublished by teacher");
        assertThat(audit("ECHO_LESSON_UNPUBLISHED", lessonId)).isEqualTo(1);
        assertThat(graph("query{teacherLessons{id reviewStatus}}", Map.of(), teacher)).contains(lessonId, "UNPUBLISHED");

        assertThat(graph("query{echoHome{totalAttempts lessons{id}}}", Map.of(), learner))
                .doesNotContain(lessonId).contains("\"totalAttempts\":1");
        int attemptsBefore = count("echo_attempts");
        assertThat(postGraph(PRACTICE, Map.of("id", lessonId + "-1", "a", "A"), learner)).contains("Learning exercise not found.");
        assertThat(count("echo_attempts")).isEqualTo(attemptsBefore);

        graph(PUBLISH, Map.of("id", lessonId), teacher);
        assertThat(status(lessonId)).isEqualTo("PUBLISHED");
        assertThat(audit("ECHO_LESSON_REPUBLISHED", lessonId)).isEqualTo(1);
        assertThat(graph("query{echoHome{lessons{id mastery attempts}}}", Map.of(), learner))
                .contains(lessonId, "\"mastery\":0.2", "\"attempts\":1");
    }

    @Test
    void draftAndArchivedExercisesCannotBePractised() {
        String teacher = teacherToken();
        String learner = register("learner");
        String draftId = saveDraft(teacher, "Still a draft", "de", null, null);
        assertThat(postGraph(PRACTICE, Map.of("id", draftId + "-1", "a", "A"), learner)).contains("Learning exercise not found.");
        db.update("update echo_lessons set review_status='ARCHIVED' where id=?", draftId);
        assertThat(postGraph(PRACTICE, Map.of("id", draftId + "-1", "a", "A"), learner)).contains("Learning exercise not found.");
    }

    @Test
    void unpublishedAudioLessonDisappearsFromRecommendationAndEvidence() {
        String teacher = teacherToken();
        String learner = register("audio-learner");
        graph("mutation{startEcho(goal:\"Understand daily routines through Danish podcasts\",languageCode:\"da\",selfReportedLevel:\"new\"){onboarded}}", Map.of(), learner);
        String lessonId = publishedLesson(teacher, "Audio to withdraw", "da", audio());
        assertThat(graph("query{echoHome{recommendedAudioLessonId}}", Map.of(), learner)).contains(lessonId);

        graph(UNPUBLISH, Map.of("id", lessonId), teacher);
        assertThat(graph("query{echoHome{recommendedAudioLessonId lessons{id}}}", Map.of(), learner)).doesNotContain(lessonId);
        String evidence = postGraph("mutation($id:ID!){submitEchoAudioEvidence(lessonId:$id,taskType:\"GIST\",answer:\"Main idea.\",transcriptUsed:false,confidence:4){saved}}",
                Map.of("id", lessonId), learner);
        assertThat(evidence).contains("Published audio lesson not found.");
        assertThat(db.queryForObject("select count(*) from echo_audio_attempts where lesson_id=?", Integer.class, lessonId)).isZero();
    }

    @Test
    void republishChecksAudioRightsAgain() {
        String teacher = teacherToken();
        String lessonId = publishedLesson(teacher, "Rights lapse", "da", audio());
        graph(UNPUBLISH, Map.of("id", lessonId), teacher);
        db.update("update echo_audio_lessons set rights_expires_at=? where lesson_id=?",
                java.sql.Date.valueOf(java.time.LocalDate.now().minusDays(1)), lessonId);
        assertThat(postGraph(PUBLISH, Map.of("id", lessonId), teacher)).contains("verify source-link rights");
        assertThat(status(lessonId)).isEqualTo("UNPUBLISHED");
        assertThat(audit("ECHO_LESSON_REPUBLISHED", lessonId)).isZero();
    }

    @Test
    void onlyTheTeacherCanUnpublish() {
        String teacher = teacherToken();
        String lessonId = publishedLesson(teacher, "Teacher only", "de", null);
        assertThat(postGraph(UNPUBLISH, Map.of("id", lessonId), register("not-teacher"))).contains("Teacher access is not enabled");
        assertThat(postGraph(UNPUBLISH, Map.of("id", lessonId), null)).contains("errors");
        assertThat(status(lessonId)).isEqualTo("PUBLISHED");
        assertThat(audit("ECHO_LESSON_UNPUBLISHED", lessonId)).isZero();
    }

    @Test
    void wrongStateAndUnknownLessonAreRejectedWithoutChanges() {
        String teacher = teacherToken();
        String draftId = saveDraft(teacher, "Draft only", "de", null, null);
        assertThat(postGraph(UNPUBLISH, Map.of("id", draftId), teacher)).contains("Only a published lesson can be unpublished.");
        assertThat(status(draftId)).isEqualTo("DRAFT");

        String lessonId = publishedLesson(teacher, "Twice", "de", null);
        graph(UNPUBLISH, Map.of("id", lessonId), teacher);
        assertThat(postGraph(UNPUBLISH, Map.of("id", lessonId), teacher)).contains("Only a published lesson can be unpublished.");
        assertThat(audit("ECHO_LESSON_UNPUBLISHED", lessonId)).isEqualTo(1);

        assertThat(postGraph(UNPUBLISH, Map.of("id", "no-such-lesson"), teacher)).contains("Published lesson not found.");
        assertThat(postGraph(UNPUBLISH, Map.of("id", " "), teacher)).contains("Choose a lesson to unpublish.");
        assertThat(postGraph(UNPUBLISH, Map.of("id", "x".repeat(121)), teacher)).contains("Choose a lesson to unpublish.");
    }

    @Test
    void revisionPublishedWhileParentIsUnpublishedArchivesTheParent() {
        String teacher = teacherToken();
        String parentId = publishedLesson(teacher, "Lineage", "de", null);
        String revisionId = saveDraft(teacher, "Lineage v2", "de", null, parentId);
        graph(UNPUBLISH, Map.of("id", parentId), teacher);

        graph(PUBLISH, Map.of("id", revisionId), teacher);
        assertThat(status(revisionId)).isEqualTo("PUBLISHED");
        assertThat(status(parentId)).isEqualTo("ARCHIVED");
        assertThat(postGraph(PUBLISH, Map.of("id", parentId), teacher)).contains("A newer revision of this lesson is published.");
        assertThat(status(parentId)).isEqualTo("ARCHIVED");
    }

    @Test
    void unpublishedLessonCanBeRevisedAndTheRevisionReplacesIt() {
        String teacher = teacherToken();
        String parentId = publishedLesson(teacher, "Fix after withdrawal", "de", null);
        graph(UNPUBLISH, Map.of("id", parentId), teacher);

        String revisionId = saveDraft(teacher, "Fix after withdrawal v2", "de", null, parentId);
        graph(PUBLISH, Map.of("id", revisionId), teacher);
        assertThat(status(revisionId)).isEqualTo("PUBLISHED");
        assertThat(status(parentId)).isEqualTo("ARCHIVED");
        assertThat(db.queryForObject("select version from echo_lessons where id=?", Integer.class, revisionId)).isEqualTo(2);
    }

    @Test
    void republishingAnUnpublishedRevisionLeavesItsArchivedParentAlone() {
        String teacher = teacherToken();
        String parentId = publishedLesson(teacher, "Revision pair", "de", null);
        String revisionId = saveDraft(teacher, "Revision pair v2", "de", null, parentId);
        graph(PUBLISH, Map.of("id", revisionId), teacher);
        graph(UNPUBLISH, Map.of("id", revisionId), teacher);
        graph(PUBLISH, Map.of("id", revisionId), teacher);
        assertThat(status(revisionId)).isEqualTo("PUBLISHED");
        assertThat(status(parentId)).isEqualTo("ARCHIVED");
    }

    private String publishedLesson(String teacher, String title, String language, Map<String, Object> audio) {
        String id = saveDraft(teacher, title, language, audio, null);
        graph(PUBLISH, Map.of("id", id), teacher);
        return id;
    }

    private String saveDraft(String teacher, String title, String language, Map<String, Object> audio, String revisionOf) {
        var exercise = Map.of("taskType", "SELECT", "prompt", "Choose the order.", "expectedAnswer", "A",
                "feedbackCorrect", "Correct.", "feedbackIncorrect", "Try again.",
                "options", List.of(Map.of("key", "A", "text", "Heute arbeite ich."), Map.of("key", "B", "text", "Heute ich arbeite.")));
        var input = new HashMap<String, Object>();
        input.put("languageCode", language); input.put("title", title); input.put("level", "A1"); input.put("topic", "Word order");
        input.put("objective", "Place the verb second."); input.put("explanation", "The finite verb is second.");
        input.put("sourceReference", "Test fixture"); input.put("skillKey", "unpublish." + UUID.randomUUID());
        input.put("exercises", List.of(exercise));
        if (audio != null) input.put("audio", audio);
        if (revisionOf != null) input.put("revisionOf", revisionOf);
        graph("mutation($input:LessonDraftInput!){saveEchoLessonDraft(input:$input){id}}", Map.of("input", input), teacher);
        return db.queryForObject("select id from echo_lessons where title=?", String.class, title);
    }

    private Map<String, Object> audio() {
        var audio = new HashMap<String, Object>();
        audio.put("mediaType", "PODCAST"); audio.put("sourceTitle", "Cleared podcast"); audio.put("creator", "Teacher-created demo");
        audio.put("sourceUrl", "https://example.test/podcast/episode"); audio.put("playbackUrl", "https://media.example.test/audio.mp3");
        audio.put("editorialBand", "A0_A1"); audio.put("topicTags", "daily life, routines"); audio.put("speechRate", 2);
        audio.put("vocabularyDensity", 2); audio.put("idiomDensity", 1); audio.put("pronunciationLoad", 2); audio.put("topicFamiliarity", 4);
        audio.put("beforeListeningPrompt", "Think about a routine."); audio.put("gistPrompt", "What is the main topic?");
        audio.put("focusPrompt", "Notice one verb."); audio.put("rightsBasis", "LICENSED"); audio.put("linkAllowed", true);
        audio.put("playbackAllowed", true); audio.put("transcriptAllowed", false);
        audio.put("rightsReference", "Teacher review record for synthetic test audio.");
        audio.put("rightsExpiresAt", java.time.LocalDate.now().plusDays(30).toString());
        return audio;
    }

    private String status(String lessonId) { return db.queryForObject("select review_status from echo_lessons where id=?", String.class, lessonId); }
    private int audit(String type, String lessonId) {
        return db.queryForObject("select count(*) from audit_events where event_type=? and aggregate_id=?", Integer.class, type, lessonId);
    }
    private int count(String table) { return db.queryForObject("select count(*) from " + table, Integer.class); }

    private String register(String name) {
        var body = http.postForObject("/api/v1/auth/register", Map.of("name", name, "email", name + "-" + UUID.randomUUID() + "@example.test",
                "password", "A-good-demo-password-2026"), Map.class);
        return (String) body.get("token");
    }
    private String teacherToken() {
        var ids = db.query("select id from accounts where email=?", (rs, row) -> rs.getString(1), "unpublish-teacher@deepwater.test");
        if (ids.isEmpty()) {
            var body = http.postForObject("/api/v1/auth/register", Map.of("name", "Teacher", "email", "unpublish-teacher@deepwater.test",
                    "password", "A-good-demo-password-2026"), Map.class);
            return (String) body.get("token");
        }
        String token = UUID.randomUUID().toString();
        db.update("insert into user_sessions(token,account_id,expires_at) values(?,?,?)", token, ids.getFirst(),
                java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(3600)));
        return token;
    }
    private String graph(String query, Map<String, ?> variables, String token) {
        String result = postGraph(query, variables, token);
        assertThat(result).doesNotContain("\"errors\"");
        return result;
    }
    private String postGraph(String query, Map<String, ?> variables, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return http.postForObject("/graphql", new HttpEntity<>(Map.of("query", query, "variables", variables), headers), String.class);
    }
}
