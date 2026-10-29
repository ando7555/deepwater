package com.deepwater;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(properties = "deepwater.teacher.email=teacher@deepwater.test")
class MvpEndToEndTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate db;

    @Test
    void teacherCanDraftPublishAndLearnerCanSelectAndPractiseGerman() {
        String teacher = teacherToken();
        assertThat(graph("query { teacherLessons { id title languageCode reviewStatus exercises{taskType expectedAnswer} } }", Map.of(), teacher))
                .contains("v2-time-first", "Start with time. Put the verb second.", "\"reviewStatus\":\"DRAFT\"", "da-01-hello", "da-11-smalltalk");
        var exercise = Map.of("taskType", "SELECT", "prompt", "Choose the correct order.",
                "expectedAnswer", "A", "feedbackCorrect", "Correct.", "feedbackIncorrect", "Try again.",
                "options", List.of(Map.of("key", "A", "text", "Heute arbeite ich."),
                        Map.of("key", "B", "text", "Heute ich arbeite.")));
        var input = Map.of("languageCode", "de", "title", "Verb second after time",
                "level", "A1", "topic", "German word order", "objective", "Place the finite verb second.",
                "explanation", "In a German main clause, the finite verb is in position two.",
                "sourceReference", "Teacher-authored example", "skillKey", "german.main-clause.v2",
                "exercises", List.of(exercise));
        String draft = graph("mutation($input:LessonDraftInput!){saveEchoLessonDraft(input:$input){id languageCode title reviewStatus}}",
                Map.of("input", input), teacher);
        assertThat(draft).contains("\"languageCode\":\"de\"", "\"reviewStatus\":\"DRAFT\"");

        String token = register("Echo Learner", "echo-" + UUID.randomUUID() + "@example.test");
        String start = graph("mutation { startEcho(goal:\"Use German at work\",languageCode:\"de\",selfReportedLevel:\"new\"){" +
                "onboarded nextAction profile{languageCode startingRoute} lessons{id}} }", Map.of(), token);
        assertThat(start).contains("\"languageCode\":\"de\"", "\"lessons\":[]", "No German lessons are published yet");

        String lessonId = db.queryForObject("select id from echo_lessons where title='Verb second after time'", String.class);
        graph("mutation($id:ID!){publishEchoLesson(lessonId:$id){reviewStatus}}", Map.of("id", lessonId), teacher);
        String chosen = graph("mutation { chooseEchoLanguage(languageCode:\"de\"){profile{languageCode} lessons{id title exercises{id}}} }",
                Map.of(), token);
        String exerciseId = lessonId + "-1";
        assertThat(chosen).contains("\"title\":\"Verb second after time\"", exerciseId)
                .doesNotContain("expectedAnswer");

        String correct = graph("mutation($id:ID!,$answer:String!){submitEchoPractice(exerciseId:$id,answer:$answer){graded correct mastery}}",
                Map.of("id", exerciseId, "answer", "A"), token);
        assertThat(correct).contains("\"graded\":true", "\"correct\":true", "\"mastery\":0.2");
        String wrong = graph("mutation($id:ID!,$answer:String!){submitEchoPractice(exerciseId:$id,answer:$answer){graded correct mastery}}",
                Map.of("id", exerciseId, "answer", "B"), token);
        assertThat(wrong).contains("\"correct\":false", "\"mastery\":0.0");
        String accountId = accountId(token);
        assertThat(db.queryForObject("select count(*) from echo_attempts where account_id=?", Integer.class, accountId)).isEqualTo(2);

        var revisionInput = new java.util.HashMap<>(input);
        revisionInput.put("revisionOf", lessonId);
        revisionInput.put("title", "Verb second after time — reviewed revision");
        graph("mutation($input:LessonDraftInput!){saveEchoLessonDraft(input:$input){id version reviewStatus}}",
                Map.of("input", revisionInput), teacher);
        String revisionId = db.queryForObject("select id from echo_lessons where revision_of=?", String.class, lessonId);
        assertThat(db.queryForObject("select version from echo_lessons where id=?", Integer.class, revisionId)).isEqualTo(2);
        graph("mutation($id:ID!){publishEchoLesson(lessonId:$id){version reviewStatus}}", Map.of("id", revisionId), teacher);
        assertThat(db.queryForObject("select review_status from echo_lessons where id=?", String.class, lessonId)).isEqualTo("ARCHIVED");
        assertThat(graph("query { echoHome { lessons{id title} } }", Map.of(), token))
                .contains("Verb second after time — reviewed revision").doesNotContain("Verb second after time\"");
    }

    @Test
    void learnerCanSeeOnlyTheirOwnProgressAndCannotOpenTeacherWorkspace() {
        String token = register("Ordinary learner", "ordinary-" + UUID.randomUUID() + "@example.test");
        assertThat(graph("query { echoHome { onboarded totalAttempts lessons{attempts mastery} } }", Map.of(), token))
                .contains("\"onboarded\":false", "\"totalAttempts\":0");
        String forbidden = postGraph("query { teacherLessons { id } }", Map.of(), token);
        assertThat(forbidden).contains("errors", "Teacher access is not enabled");
        String otherToken = register("Another learner", "other-" + UUID.randomUUID() + "@example.test");
        assertThat(graph("query { echoHome { onboarded totalAttempts } }", Map.of(), otherToken))
                .contains("\"onboarded\":false", "\"totalAttempts\":0");
    }

    @Test
    void anonymousWorkspaceDoesNotExposeUnapprovedDrafts() {
        String result = postGraph("query { echoHome { onboarded totalAttempts lessons{id} } }", Map.of(), null);
        assertThat(result).doesNotContain("errors").contains("\"onboarded\":false", "\"lessons\":[]");
    }

    @Test
    void audioLessonRequiresTeacherApprovalAndHonorsTranscriptRights() {
        String teacher = teacherToken();
        String learner = register("Audio Learner", "audio-learner-" + UUID.randomUUID() + "@example.test");
        graph("mutation{startEcho(goal:\"Understand daily routines through Danish podcasts\",languageCode:\"da\",selfReportedLevel:\"new\"){onboarded profile{languageCode}}}", Map.of(), learner);
        var unverifiedAudio = audioDraft("UNVERIFIED", false);
        var unverifiedInput = audioLessonInput("Private audio draft", unverifiedAudio);
        String draft = graph("mutation($input:LessonDraftInput!){saveEchoLessonDraft(input:$input){id reviewStatus audio{mediaType editorialBand rightsBasis}}}",
                Map.of("input", unverifiedInput), teacher);
        assertThat(draft).contains("\"reviewStatus\":\"DRAFT\"", "\"rightsBasis\":\"UNVERIFIED\"");
        String draftId = db.queryForObject("select id from echo_lessons where title='Private audio draft'", String.class);
        assertThat(graph("query{echoHome{lessons{id audio{sourceTitle}}}}", Map.of(), learner)).doesNotContain(draftId, "Uncleared podcast");
        String refused = postGraph("mutation($id:ID!){publishEchoLesson(lessonId:$id){reviewStatus}}", Map.of("id", draftId), teacher);
        assertThat(refused).contains("errors", "verify source-link rights");
        assertThat(db.queryForObject("select review_status from echo_lessons where id=?", String.class, draftId)).isEqualTo("DRAFT");

        var clearedAudio = audioDraft("LICENSED", true);
        var clearedInput = audioLessonInput("Rights-cleared podcast", clearedAudio);
        String cleared = graph("mutation($input:LessonDraftInput!){saveEchoLessonDraft(input:$input){id reviewStatus audio{mediaType editorialBand transcriptAllowed}}}",
                Map.of("input", clearedInput), teacher);
        assertThat(cleared).contains("\"mediaType\":\"PODCAST\"", "\"editorialBand\":\"A0_A1\"");
        String clearedId = db.queryForObject("select id from echo_lessons where title='Rights-cleared podcast'", String.class);
        graph("mutation($id:ID!){publishEchoLesson(lessonId:$id){reviewStatus}}", Map.of("id", clearedId), teacher);
        String visible = graph("query{echoHome{audioAttempts recommendedAudioLessonId recommendedAudioReason lessons{id audio{mediaType sourceUrl playbackUrl transcriptAllowed transcriptText}}}}", Map.of(), learner);
        assertThat(visible).contains(clearedId, "\"recommendedAudioLessonId\":\"" + clearedId + "\"",
                "A0–A1 beginner", "\"transcriptAllowed\":true", "Teacher-authored transcript example.", "https://media.example.test/audio.mp3");

        String saved = graph("mutation($id:ID!){submitEchoAudioEvidence(lessonId:$id,taskType:\"GIST\",answer:\"I heard the main idea.\",transcriptUsed:true,confidence:4){saved taskType transcriptUsed confidence nextTaskType repeatSuggested}}",
                Map.of("id", clearedId), learner);
        assertThat(saved).contains("\"saved\":true", "\"transcriptUsed\":true", "\"confidence\":4", "\"nextTaskType\":\"PHRASE\"", "\"repeatSuggested\":false");
        assertThat(db.queryForObject("select response_chars from echo_audio_attempts where lesson_id=?", Integer.class, clearedId))
                .isEqualTo("I heard the main idea.".length());
        assertThat(db.queryForObject("select count(*) from echo_audio_attempts where lesson_id=? and account_id=?", Integer.class,
                clearedId, accountId(teacher))).isZero();
        String repeat = graph("mutation($id:ID!){submitEchoAudioEvidence(lessonId:$id,taskType:\"GIST\",answer:\"I want one more listen.\",transcriptUsed:false,confidence:1){nextTaskType repeatSuggested feedback}}",
                Map.of("id", clearedId), learner);
        assertThat(repeat).contains("\"nextTaskType\":\"GIST\"", "\"repeatSuggested\":true", "confidence was low");
        assertThat(graph("query{echoHome{audioAttempts}}", Map.of(), learner)).contains("\"audioAttempts\":2");
    }

    private Map<String, Object> audioDraft(String rightsBasis, boolean transcriptAllowed) {
        var audio = new java.util.HashMap<String, Object>();
        audio.put("mediaType", "PODCAST"); audio.put("sourceTitle", "Uncleared podcast");
        audio.put("creator", "Teacher-created demo"); audio.put("sourceUrl", "https://example.test/podcast/episode");
        audio.put("playbackUrl", "https://media.example.test/audio.mp3"); audio.put("editorialBand", "A0_A1");
        audio.put("topicTags", "daily life, routines"); audio.put("speechRate", 2); audio.put("vocabularyDensity", 2);
        audio.put("idiomDensity", 1); audio.put("pronunciationLoad", 2); audio.put("topicFamiliarity", 4);
        audio.put("beforeListeningPrompt", "Think about a familiar daily routine.");
        audio.put("gistPrompt", "What is the speaker mainly talking about?");
        audio.put("focusPrompt", "Notice one present-tense verb.");
        audio.put("rightsBasis", rightsBasis); audio.put("linkAllowed", "UNVERIFIED".equals(rightsBasis) ? false : true);
        audio.put("playbackAllowed", true); audio.put("transcriptAllowed", transcriptAllowed);
        audio.put("rightsReference", "Teacher review record for synthetic test audio.");
        if (transcriptAllowed) audio.put("transcriptText", "Teacher-authored transcript example.");
        audio.put("rightsExpiresAt", java.time.LocalDate.now().plusDays(30).toString());
        return audio;
    }

    private Map<String, Object> audioLessonInput(String title, Map<String, Object> audio) {
        var exercise = Map.<String, Object>of("taskType", "TRANSFER",
                "prompt", "Create an original expression using the lesson's target.");
        var input = new java.util.HashMap<String, Object>();
        input.put("languageCode", "da"); input.put("title", title); input.put("level", "A0–A1 · Beginner");
        input.put("topic", "Daily routines"); input.put("objective", "Understand a short spoken description of a routine.");
        input.put("explanation", "Listen for the gist first. The teacher-approved transcript is optional support.");
        input.put("sourceReference", "Teacher-curated podcast source"); input.put("skillKey", "audio." + title.toLowerCase().replace(' ', '-'));
        input.put("exercises", List.of(exercise)); input.put("audio", audio);
        return input;
    }

    private String register(String name, String email) {
        var body = http.postForObject("/api/v1/auth/register", Map.of("name", name, "email", email,
                "password", "A-good-demo-password-2026"), Map.class);
        assertThat(body).containsKey("token");
        return (String) body.get("token");
    }
    private String teacherToken() {
        var ids = db.query("select id from accounts where email=?", (rs, row) -> rs.getString(1), "teacher@deepwater.test");
        if (ids.isEmpty()) return register("Echo Teacher", "teacher@deepwater.test");
        String token = UUID.randomUUID().toString();
        db.update("insert into user_sessions(token,account_id,expires_at) values(?,?,?)", token, ids.getFirst(),
                java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(3600)));
        return token;
    }
    private String accountId(String token) {
        return db.queryForObject("select account_id from user_sessions where token=?", String.class, token);
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
