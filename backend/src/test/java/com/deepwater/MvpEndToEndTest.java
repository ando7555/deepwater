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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class MvpEndToEndTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate db;

    @Test
    void learnerCanOnboardPracticeTrackMasteryAndSaveAnUngradedTransferExample() {
        String suffix = UUID.randomUUID().toString();
        String token = register("Echo Learner", "echo-" + suffix + "@example.test");
        assertThat(graph("query { echoHome { onboarded nextAction } }", Map.of(), token))
                .contains("\"onboarded\":false");

        var diagnostic = List.of(Map.of("id", "v2", "answer", "B"),
                Map.of("id", "definite", "answer", "C"), Map.of("id", "present", "answer", "B"));
        var start = graph("mutation($goal:String!,$level:String!,$answers:[DiagnosticAnswerInput!]!){" +
                        "startEcho(goal:$goal,selfReportedLevel:$level,answers:$answers){" +
                        "onboarded profile{diagnosticScore startingRoute} lessons{id title sourceReference contentStatus " +
                        "exercises{id taskType prompt options{key text}}}}}",
                Map.of("goal", "Use Danish at work", "level", "new", "answers", diagnostic), token);
        assertThat(start).contains("\"diagnosticScore\":3", "\"startingRoute\":\"CHALLENGE\"",
                "I dag arbejder jeg hjemme", "Danish teacher review required");
        assertThat(start).doesNotContain("expectedAnswer");

        String correct = graph("mutation($id:ID!,$answer:String!){submitEchoPractice(exerciseId:$id,answer:$answer){" +
                        "graded correct mastery nextReviewAt feedback}}",
                Map.of("id", "v2-select", "answer", "A"), token);
        assertThat(correct).contains("\"graded\":true", "\"correct\":true", "\"mastery\":0.2");
        String wrong = graph("mutation($id:ID!,$answer:String!){submitEchoPractice(exerciseId:$id,answer:$answer){graded correct mastery}}",
                Map.of("id", "v2-select", "answer", "B"), token);
        assertThat(wrong).contains("\"correct\":false", "\"mastery\":0.0");

        String transfer = graph("mutation($id:ID!,$answer:String!,$checked:Boolean!){submitEchoPractice(" +
                        "exerciseId:$id,answer:$answer,transferSelfCheck:$checked){graded correct transfer feedback mastery}}",
                Map.of("id", "v2-transfer", "answer", "I morgen arbejder jeg hjemme.", "checked", true), token);
        assertThat(transfer).contains("\"graded\":false", "\"correct\":null", "\"transfer\":true",
                "does not automatically score free writing", "\"mastery\":0.0");
        String accountId = accountId(token);
        assertThat(db.queryForObject("select count(*) from echo_attempts where account_id=?", Integer.class, accountId)).isEqualTo(3);
        assertThat(db.queryForObject("select count(*) from echo_attempts where account_id=? and is_transfer=true and is_correct is null", Integer.class, accountId)).isEqualTo(1);

        String otherToken = register("Another Learner", "other-" + suffix + "@example.test");
        assertThat(graph("query { echoHome { onboarded totalAttempts lessons{attempts mastery} } }", Map.of(), otherToken))
                .contains("\"onboarded\":false", "\"totalAttempts\":0", "\"mastery\":0.0");
    }

    @Test
    void anonymousEchoHomeShowsLessonContentButNoLearnerData() {
        var result = postGraph("query { echoHome { onboarded totalAttempts lessons{mastery attempts exercises{lastCorrect}} } }", Map.of(), null);
        assertThat(result).doesNotContain("errors").contains("\"onboarded\":false", "\"totalAttempts\":0",
                "\"mastery\":0.0", "\"attempts\":0", "\"lastCorrect\":null");
    }

    @Test
    void startingCheckRejectsMissingAnswers() {
        String token = register("Incomplete Learner", "incomplete-" + UUID.randomUUID() + "@example.test");
        String result = postGraph("mutation { startEcho(goal:\"Work\",selfReportedLevel:\"new\",answers:[]){onboarded} }",
                Map.of(), token);
        assertThat(result).contains("errors", "Answer all three starting-check questions");
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
        var entity = new HttpEntity<>(Map.of("query", query, "variables", variables), headers);
        return http.postForObject("/graphql", entity, String.class);
    }
}
