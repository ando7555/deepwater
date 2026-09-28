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
        String teacher = register("Echo Teacher", "teacher@deepwater.test");
        assertThat(graph("query { teacherLessons { id title languageCode reviewStatus exercises{taskType expectedAnswer} } }", Map.of(), teacher))
                .contains("v2-time-first", "Start with time. Put the verb second.", "\"reviewStatus\":\"DRAFT\"");
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

    private String register(String name, String email) {
        var body = http.postForObject("/api/v1/auth/register", Map.of("name", name, "email", email,
                "password", "A-good-demo-password-2026"), Map.class);
        assertThat(body).containsKey("token");
        return (String) body.get("token");
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
