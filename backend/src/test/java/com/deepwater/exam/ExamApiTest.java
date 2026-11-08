package com.deepwater.exam;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

/** The Polish B1 practice exam end to end: tasks without answers, marking, the pass rule, history and deletion. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ExamApiTest {
    private static final String PASSWORD = "A-good-demo-password-2026";
    static final String INVITATION = """
            Cześć Ola! Zapraszam cię na moje urodziny, które będą w sobotę o godzinie osiemnastej. Przyjęcie zrobię w moim nowym
            mieszkaniu na ulicy Długiej. Planuję grilla w ogrodzie, a potem będziemy tańczyć, bo przyjdzie też mój brat z gitarą.
            Jeśli chcesz, możesz przyjść z chłopakiem. Najłatwiej dojechać do mnie tramwajem numer cztery. Wysiądź na przystanku
            Rynek i idź prosto pięć minut. Kiedy będziesz na miejscu, zadzwoń, ponieważ domofon nie działa. Daj mi znać do środy,
            czy przyjdziesz. Ściskam, Ewa.""";
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate db;
    @Autowired ExamContent content;
    @Autowired ObjectMapper json;

    @Test
    void theExamHasFivePartsAndHidesTheAnswers() {
        String token = register();
        String exam = send(HttpMethod.GET, "/api/v1/exam/polish-b1", token, null).getBody();
        assertThat(exam).contains("Rozumienie ze słuchu", "Poprawność gramatyczna", "Rozumienie tekstów pisanych", "Pisanie", "Mówienie",
                "\"passPercent\":60", "\"aiExaminer\":false", "not official exam tasks")
                .doesNotContain("\"answer\"", "topicWords");
        assertThat(content.exam().parts()).hasSize(5);
        assertThat(http.getForEntity("/api/v1/exam/polish-b1", String.class).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void closedPartsAreMarkedByTheKeyAndOpenPartsByTheRubricWithoutAKey() {
        String token = register();
        Map<String, String> answers = correctClosedAnswers();
        answers.put("w1", INVITATION);
        answers.put("s1", "Hello, my name is Ana and I like music very much and I live in Copenhagen with my family.");
        var response = send(HttpMethod.POST, "/api/v1/exam/polish-b1/attempts", token, Map.of("answers", answers));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"markedBy\":\"RULES\"", "\"readyForB1\":false", "Not ready for B1 yet",
                "\"id\":\"listening\",\"name\":\"Rozumienie ze słuchu\",\"english\":\"Listening\",\"percent\":100,\"passed\":true",
                "\"id\":\"grammar\"", "This does not look like Polish", "No answer was given.");

        String history = send(HttpMethod.GET, "/api/v1/exam/polish-b1/attempts", token, null).getBody();
        assertThat(history).contains("\"readyForB1\":false", "\"levelEstimate\":");
        String id = json.readTree(history).get(0).get("id").asString();
        assertThat(send(HttpMethod.GET, "/api/v1/exam/polish-b1/attempts/" + id, token, null).getBody()).contains("\"markedBy\":\"RULES\"");
        // Another learner cannot open it.
        assertThat(send(HttpMethod.GET, "/api/v1/exam/polish-b1/attempts/" + id, register(), null).getStatusCode().value()).isEqualTo(404);

        String accountId = accountId(token);
        String email = db.queryForObject("select email from accounts where id=?", String.class, accountId);
        assertThat(send(HttpMethod.DELETE, "/api/v1/auth/account", token, Map.of("confirmEmail", email)).getStatusCode().value()).isEqualTo(204);
        assertThat(db.queryForObject("select count(*) from exam_attempts where account_id=?", Integer.class, accountId)).isZero();
    }

    @Test
    void everyPartMustReachThePassMarkToLookReady() {
        String accountId = accountId(register());
        WritingAssessor generous = answers -> new WritingAssessor.Review(answers.stream().collect(java.util.stream.Collectors.toMap(
                WritingAssessor.OpenAnswer::itemId, a -> mark(76))), "B1", "Solid B1 writing.", WritingAssessor.AI);
        ExamService service = new ExamService(content, generous, db, json, 60);
        var ready = service.submit(accountId, correctClosedAnswers());
        assertThat(ready.readyForB1()).isTrue();
        // Closed parts at 100% and open parts at 76% average 90%, a strong pass.
        assertThat(ready.levelEstimate()).isEqualTo("B1+");
        assertThat(ready.examinerLevel()).isEqualTo("B1");
        assertThat(ready.verdict()).contains("You look ready for the B1 exam", "not an official result");

        Map<String, String> oneGrammarMiss = correctClosedAnswers();
        for (String id : List.of("g1", "g2", "g3", "g4")) oneGrammarMiss.put(id, "2");
        var notReady = service.submit(accountId, oneGrammarMiss);
        assertThat(notReady.readyForB1()).isFalse();
        assertThat(notReady.verdict()).contains("Work on: Grammar");
    }

    @Test
    void whenTheAiFailsTheRubricMarksEveryAnswer() {
        String accountId = accountId(register());
        WritingAssessor broken = answers -> { throw new IllegalStateException("network down"); };
        var result = new ExamService(content, broken, db, json, 60).submit(accountId, Map.of("w1", INVITATION));
        assertThat(result.markedBy()).isEqualTo(WritingAssessor.RULES);
        var writing = result.parts().stream().filter(p -> p.id().equals("writing")).findFirst().orElseThrow();
        assertThat(writing.items().getFirst().assessment().score()).isGreaterThan(40);

        // An AI review that skips an answer gets the rest marked by the rubric.
        WritingAssessor partial = answers -> new WritingAssessor.Review(Map.of("w1", mark(70)), "B1", null, WritingAssessor.AI);
        var mixed = new ExamService(content, partial, db, json, 60).submit(accountId, Map.of("w1", INVITATION));
        assertThat(mixed.parts().stream().flatMap(p -> p.items().stream()).filter(i -> i.assessment() != null)
                .map(i -> i.assessment().markedBy()).distinct()).containsExactlyInAnyOrder("AI", "RULES");
    }

    @Test
    void overlongAnswersAreRefused() {
        String token = register();
        var response = send(HttpMethod.POST, "/api/v1/exam/polish-b1/attempts", token, Map.of("answers", Map.of("w1", "a".repeat(4001))));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    private static WritingAssessor.ItemAssessment mark(int score) {
        int each = score / 4;
        return new WritingAssessor.ItemAssessment(each * 4, new WritingAssessor.Criteria(each, each, each, each), "B1", "ok",
                List.of(), List.of(), List.of(), WritingAssessor.AI);
    }

    private Map<String, String> correctClosedAnswers() {
        Map<String, String> answers = new HashMap<>();
        content.exam().parts().stream().filter(ExamContent.Part::closed).flatMap(p -> p.items().stream())
                .forEach(i -> answers.put(i.id(), String.valueOf(i.answer())));
        return answers;
    }

    private String accountId(String token) {
        return db.queryForObject("select account_id from user_sessions where token=?", String.class, token);
    }

    private String register() {
        var body = http.postForObject("/api/v1/auth/register", Map.of("name", "Exam Tester",
                "email", "exam-" + UUID.randomUUID() + "@example.test", "password", PASSWORD), Map.class);
        return (String) body.get("token");
    }

    private ResponseEntity<String> send(HttpMethod method, String path, String token, Object body) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }
}
