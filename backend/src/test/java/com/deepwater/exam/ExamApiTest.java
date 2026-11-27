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

/** The practice exams end to end: tasks without answers, marking, the pass rule, history and deletion. */
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
    static final String HEATING = """
            Kære Lars. Jeg skriver til dig, fordi varmen i min lejlighed på Vestergade 12 ikke har virket i to uger. Alle radiatorer
            er kolde, selvom jeg har skruet helt op for dem. Jeg ringede til dig i sidste uge og lagde en besked, men jeg har desværre
            ikke hørt fra dig endnu. Derfor har jeg nu købt en lille elradiator, men den kan kun varme ét værelse op, og min elregning
            bliver meget højere. Desuden er det svært for mig at arbejde hjemme, når der kun er sytten grader i stuen. Jeg forventer,
            at du sender en VVS-mand inden fredag, så varmen kan blive repareret. Hvis det ikke er muligt, vil jeg gerne have en del af
            huslejen tilbage for de uger, hvor lejligheden har været kold. Du kan ringe til mig på 22 33 44 55 hele dagen.
            Med venlig hilsen, Amira Haddad""";
    static final String MOVE = """
            Liebe Julia, endlich habe ich eine neue Wohnung gefunden! Sie liegt im dritten Stock und hat zwei helle Zimmer, eine
            kleine Küche und einen Balkon mit Blick auf den Park. Ich bin umgezogen, weil meine alte Wohnung zu weit von meiner
            Arbeit entfernt war. Jetzt fahre ich nur zehn Minuten mit dem Fahrrad, deshalb habe ich morgens mehr Zeit. Außerdem
            ist die Miete nicht teurer als vorher. Am Samstag, den 6. Dezember, mache ich eine Einweihungsparty und möchte dich
            herzlich einladen. Wir fangen um sieben Uhr an. Kannst du vielleicht einen Salat mitbringen? Ich freue mich auf dich!
            Viele Grüße, Amira""";
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate db;
    @Autowired ExamContent content;
    @Autowired ObjectMapper json;

    @Test
    void theDanishExamHasTheFourPartsOfPd3() {
        String token = register();
        String exam = send(HttpMethod.GET, "/api/v1/exam/danish-pd3", token, null).getBody();
        assertThat(exam).contains("Lytteforståelse", "Læseforståelse", "Skriftlig fremstilling", "Mundtlig kommunikation",
                "\"language\":\"da\"", "\"level\":\"B2\"", "\"shortName\":\"PD3 (B2)\"", "not official test tasks")
                .doesNotContain("\"answer\"", "topicWords");
        assertThat(content.exam("danish-pd3").parts()).hasSize(4);
        assertThat(send(HttpMethod.GET, "/api/v1/exam/swedish-sfi", token, null).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void theGermanExamHasTheFourGoetheModulesAndIsMarkedInGerman() {
        String token = register();
        String exam = send(HttpMethod.GET, "/api/v1/exam/german-b1", token, null).getBody();
        assertThat(exam).contains("\"name\":\"Lesen\"", "\"name\":\"Hören\"", "\"name\":\"Schreiben\"", "\"name\":\"Sprechen\"",
                "\"language\":\"de\"", "\"level\":\"B1\"", "not official exam tasks").doesNotContain("\"answer\"", "topicWords");
        assertThat(content.exam("german-b1").parts()).hasSize(4);

        Map<String, String> answers = correctClosedAnswers("german-b1");
        answers.put("w1", MOVE);
        answers.put("w3", "I cannot come on Friday, sorry, because I have to work late that evening.");
        var response = send(HttpMethod.POST, "/api/v1/exam/german-b1/attempts", token, Map.of("answers", answers));
        assertThat(response.getBody()).contains("\"examId\":\"german-b1\"", "\"targetLevel\":\"B1\"", "This does not look like German",
                "\"name\":\"Lesen\",\"english\":\"Reading\",\"percent\":100");
        var writing = json.readTree(response.getBody()).get("parts").get(2).get("items").get(0).get("assessment");
        assertThat(writing.get("score").asInt()).isGreaterThanOrEqualTo(60);
        assertThat(json.readTree(send(HttpMethod.GET, "/api/v1/exam/danish-pd3/attempts", token, null).getBody())).isEmpty();
    }

    @Test
    void danishAttemptsAreMarkedInDanishAndKeptApartFromPolish() {
        String token = register();
        Map<String, String> answers = correctClosedAnswers("danish-pd3");
        answers.put("w1", HEATING);
        answers.put("s1", "Zapraszam cię na moje urodziny w sobotę, bo będzie impreza w moim mieszkaniu.");
        var response = send(HttpMethod.POST, "/api/v1/exam/danish-pd3/attempts", token, Map.of("answers", answers));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"examId\":\"danish-pd3\"", "\"targetLevel\":\"B2\"", "Not ready for PD3 (B2) yet",
                "This does not look like Danish", "\"name\":\"Lytteforståelse\",\"english\":\"Listening\",\"percent\":100");
        String id = json.readTree(response.getBody()).get("id").asString();

        assertThat(json.readTree(send(HttpMethod.GET, "/api/v1/exam/danish-pd3/attempts", token, null).getBody())).hasSize(1);
        assertThat(json.readTree(send(HttpMethod.GET, "/api/v1/exam/polish-b1/attempts", token, null).getBody())).isEmpty();
        assertThat(send(HttpMethod.GET, "/api/v1/exam/polish-b1/attempts/" + id, token, null).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void aDanishPassIsJudgedAgainstB2() {
        String accountId = accountId(register());
        WritingAssessor generous = (target, answers) -> new WritingAssessor.Review(answers.stream().collect(java.util.stream.Collectors.toMap(
                WritingAssessor.OpenAnswer::itemId, a -> mark(76))), "B2", "Solid B2.", WritingAssessor.AI);
        var ready = new ExamService(content, generous, db, json, 60).submit(accountId, "danish-pd3", correctClosedAnswers("danish-pd3"));
        assertThat(ready.ready()).isTrue();
        assertThat(ready.levelEstimate()).isEqualTo("B2+");
        assertThat(ready.verdict()).startsWith("You look ready for PD3 (B2)");

        var weak = new ExamService(content, generous, db, json, 60).submit(accountId, "danish-pd3", Map.of());
        assertThat(weak.ready()).isFalse();
        // Closed parts at 0% and open parts at 76% average 38%: well below the pass mark.
        assertThat(weak.levelEstimate()).isEqualTo("A2-B1");
    }

    @Test
    void attemptsSavedBeforeDanishWasAddedStillOpen() throws Exception {
        String accountId = accountId(register());
        String old = """
                {"id":"old-1","examId":"polish-b1","createdAt":"2026-11-10T10:00:00Z","parts":[],"averagePercent":72,
                 "passPercent":60,"readyForB1":true,"levelEstimate":"B1","verdict":"You look ready for the B1 exam","markedBy":"RULES"}""";
        db.update("insert into exam_attempts(id,account_id,exam_id,eligible,level_estimate,average_percent,result) values(?,?,?,?,?,?,?)",
                "old-1", accountId, "polish-b1", true, "B1", 72, old);
        var attempt = new ExamService(content, new RuleWritingAssessor(), db, json, 60).attempt(accountId, "polish-b1", "old-1");
        assertThat(attempt.ready()).isTrue();
        assertThat(attempt.targetLevel()).isNull();
    }

    @Test
    void theExamHasFivePartsAndHidesTheAnswers() {
        String token = register();
        String exam = send(HttpMethod.GET, "/api/v1/exam/polish-b1", token, null).getBody();
        assertThat(exam).contains("Rozumienie ze słuchu", "Poprawność gramatyczna", "Rozumienie tekstów pisanych", "Pisanie", "Mówienie",
                "\"passPercent\":60", "\"aiExaminer\":false", "not official exam tasks")
                .doesNotContain("\"answer\"", "topicWords");
        assertThat(content.exam("polish-b1").parts()).hasSize(5);
        assertThat(http.getForEntity("/api/v1/exam/polish-b1", String.class).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void closedPartsAreMarkedByTheKeyAndOpenPartsByTheRubricWithoutAKey() {
        String token = register();
        Map<String, String> answers = correctClosedAnswers("polish-b1");
        answers.put("w1", INVITATION);
        answers.put("s1", "Hello, my name is Ana and I like music very much and I live in Copenhagen with my family.");
        var response = send(HttpMethod.POST, "/api/v1/exam/polish-b1/attempts", token, Map.of("answers", answers));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"markedBy\":\"RULES\"", "\"ready\":false", "Not ready for B1 yet",
                "\"id\":\"listening\",\"name\":\"Rozumienie ze słuchu\",\"english\":\"Listening\",\"percent\":100,\"passed\":true",
                "\"id\":\"grammar\"", "This does not look like Polish", "No answer was given.");

        String history = send(HttpMethod.GET, "/api/v1/exam/polish-b1/attempts", token, null).getBody();
        assertThat(history).contains("\"ready\":false", "\"levelEstimate\":");
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
        WritingAssessor generous = (target, answers) -> new WritingAssessor.Review(answers.stream().collect(java.util.stream.Collectors.toMap(
                WritingAssessor.OpenAnswer::itemId, a -> mark(76))), "B1", "Solid B1 writing.", WritingAssessor.AI);
        ExamService service = new ExamService(content, generous, db, json, 60);
        var ready = service.submit(accountId, "polish-b1", correctClosedAnswers("polish-b1"));
        assertThat(ready.ready()).isTrue();
        assertThat(ready.targetLevel()).isEqualTo("B1");
        // Closed parts at 100% and open parts at 76% average 90%, a strong pass.
        assertThat(ready.levelEstimate()).isEqualTo("B1+");
        assertThat(ready.examinerLevel()).isEqualTo("B1");
        assertThat(ready.verdict()).contains("You look ready for B1", "not an official result");

        Map<String, String> oneGrammarMiss = correctClosedAnswers("polish-b1");
        for (String id : List.of("g1", "g2", "g3", "g4")) oneGrammarMiss.put(id, "2");
        var notReady = service.submit(accountId, "polish-b1", oneGrammarMiss);
        assertThat(notReady.ready()).isFalse();
        assertThat(notReady.verdict()).contains("Work on: Grammar");
    }

    @Test
    void whenTheAiFailsTheRubricMarksEveryAnswer() {
        String accountId = accountId(register());
        WritingAssessor broken = (target, answers) -> { throw new IllegalStateException("network down"); };
        var result = new ExamService(content, broken, db, json, 60).submit(accountId, "polish-b1", Map.of("w1", INVITATION));
        assertThat(result.markedBy()).isEqualTo(WritingAssessor.RULES);
        var writing = result.parts().stream().filter(p -> p.id().equals("writing")).findFirst().orElseThrow();
        assertThat(writing.items().getFirst().assessment().score()).isGreaterThan(40);

        // An AI review that skips an answer gets the rest marked by the rubric.
        WritingAssessor partial = (target, answers) -> new WritingAssessor.Review(Map.of("w1", mark(70)), "B1", null, WritingAssessor.AI);
        var mixed = new ExamService(content, partial, db, json, 60).submit(accountId, "polish-b1", Map.of("w1", INVITATION));
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

    private Map<String, String> correctClosedAnswers(String examId) {
        Map<String, String> answers = new HashMap<>();
        content.exam(examId).parts().stream().filter(ExamContent.Part::closed).flatMap(p -> p.items().stream())
                .forEach(i -> answers.put(i.id(), String.valueOf(i.answer())));
        return answers;
    }

    private String accountId(String token) {
        return db.queryForObject("select account_id from user_sessions where token=?", String.class, token);
    }

    @Test
    void everyExamHasFivePracticeTestsWithTheSamePartsAndTheirOwnHistory() throws Exception {
        String token = register();
        for (String series : List.of("polish-b1", "danish-pd3", "german-b1")) {
            List<String> tests = content.tests(series);
            assertThat(tests).hasSize(5).first().isEqualTo(series);
            for (int n = 0; n < tests.size(); n++) {
                var exam = json.readTree(send(HttpMethod.GET, "/api/v1/exam/" + tests.get(n), token, null).getBody()).get("exam");
                assertThat(exam.get("testNumber").asInt()).isEqualTo(n + 1);
                assertThat(exam.get("tests")).hasSize(5);
                assertThat(exam.get("language").asText()).isEqualTo(content.exam(series).language());
                assertThat(exam.toString()).doesNotContain("\"answer\"", "topicWords");
                assertThat(content.exam(tests.get(n)).parts().stream().map(ExamContent.Part::id).toList())
                        .isEqualTo(content.exam(series).parts().stream().map(ExamContent.Part::id).toList());
            }
        }
        assertThat(content.exam("polish-b1-3").title()).isEqualTo("Polish B1 practice exam · test 3");
        // An attempt at test 2 shows up in test 2's history only.
        var response = send(HttpMethod.POST, "/api/v1/exam/polish-b1-2/attempts", token, Map.of("answers", Map.of("l1", "1")));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(json.readTree(send(HttpMethod.GET, "/api/v1/exam/polish-b1-2/attempts", token, null).getBody())).hasSize(1);
        assertThat(json.readTree(send(HttpMethod.GET, "/api/v1/exam/polish-b1/attempts", token, null).getBody())).isEmpty();
    }

    @Test
    void pricingShowsTheOwnersLaunchPricesAndNoLinkUntilOneIsSet() throws Exception {
        var polish = json.readTree(http.getForObject("/api/v1/pricing?language=pl", String.class));
        assertThat(polish.get("launchBuyers").asInt()).isEqualTo(50);
        assertThat(polish.get("offers")).hasSize(1);
        var exam = polish.get("offers").get(0);
        assertThat(exam.get("id").asText()).isEqualTo("exam");
        assertThat(exam.get("currency").asText()).isEqualTo("PLN");
        assertThat(exam.get("launchPrice").asInt()).isEqualTo(100);
        assertThat(exam.get("fullPrice").asInt()).isEqualTo(720);
        assertThat(exam.get("link").isNull()).isTrue();
        var danish = json.readTree(http.getForObject("/api/v1/pricing?language=da", String.class)).get("offers");
        assertThat(danish).hasSize(2);
        assertThat(danish.get(0).get("launchPrice").asInt()).isEqualTo(175);
        assertThat(danish.get(1).get("fullPrice").asInt()).isEqualTo(1249);
        var german = json.readTree(http.getForObject("/api/v1/pricing?language=de", String.class)).get("offers");
        assertThat(german.get(0).get("currency").asText()).isEqualTo("EUR");
        assertThat(german.get(0).get("launchPrice").asInt()).isEqualTo(25);
        assertThat(json.readTree(http.getForObject("/api/v1/pricing?language=sv", String.class)).get("offers")).isEmpty();
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
