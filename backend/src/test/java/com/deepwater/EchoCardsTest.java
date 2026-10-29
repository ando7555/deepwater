package com.deepwater;

import static org.assertj.core.api.Assertions.assertThat;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class EchoCardsTest {
    private static final String SAVE = "mutation($front:String!,$back:String!){saveEchoCard(front:$front,back:$back){id box due}}";
    private static final String REVIEW = "mutation($id:ID!,$knew:Boolean!){reviewEchoCard(cardId:$id,knewIt:$knew){box due nextReviewAt}}";
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate db;

    @Test
    void learnerSavesAPhraseAndAMissMovesItBackOneBoxNotToTheStart() {
        String token = learner("da");
        String saved = graph(SAVE, Map.of("front", "Jeg kan godt lide kaffe", "back", "I like coffee"), token);
        assertThat(saved).contains("\"box\":0", "\"due\":true");
        String id = saved.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        assertThat(graph(REVIEW, Map.of("id", id, "knew", true), token)).contains("\"box\":1", "\"due\":false");
        assertThat(graph(REVIEW, Map.of("id", id, "knew", true), token)).contains("\"box\":2");
        assertThat(graph(REVIEW, Map.of("id", id, "knew", false), token)).contains("\"box\":1", "\"due\":false");

        assertThat(graph("query { echoCards { front back box } }", Map.of(), token))
                .contains("Jeg kan godt lide kaffe", "I like coffee");
    }

    @Test
    void cardsBelongToOneLearnerAndOneLanguage() {
        String owner = learner("da");
        String other = learner("da");
        String saved = graph(SAVE, Map.of("front", "Hej", "back", "Hi"), owner);
        String id = saved.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        assertThat(graph("query { echoCards { id } }", Map.of(), other)).contains("\"echoCards\":[]");
        assertThat(postGraph(REVIEW, Map.of("id", id, "knew", true), other)).contains("Card not found.");
        assertThat(postGraph("mutation($id:ID!){deleteEchoCard(cardId:$id)}", Map.of("id", id), other)).contains("Card not found.");

        graph("mutation { chooseEchoLanguage(languageCode:\"de\"){ profile { languageCode } } }", Map.of(), owner);
        assertThat(graph("query { echoCards { id } }", Map.of(), owner)).contains("\"echoCards\":[]");
        graph("mutation { chooseEchoLanguage(languageCode:\"da\"){ profile { languageCode } } }", Map.of(), owner);
        assertThat(graph("mutation($id:ID!){deleteEchoCard(cardId:$id)}", Map.of("id", id), owner)).contains("true");
        assertThat(graph("query { echoCards { id } }", Map.of(), owner)).contains("\"echoCards\":[]");
    }

    @Test
    void closeKeepsTheBoxAndAMissRemembersWhatWasTyped() {
        String token = learner("da");
        String id = graph(SAVE, Map.of("front", "en kat", "back", "a cat"), token).replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        String grade = "mutation($id:ID!,$grade:CardGrade!,$answer:String){reviewEchoCard(cardId:$id,grade:$grade,answer:$answer){box due missCount lastMiss}}";

        assertThat(graph(grade, Map.of("id", id, "grade", "KNEW"), token)).contains("\"box\":1", "\"missCount\":0");
        assertThat(graph(grade, Map.of("id", id, "grade", "CLOSE"), token)).contains("\"box\":1", "\"due\":false");
        assertThat(graph(grade, Map.of("id", id, "grade", "MISSED", "answer", "et kat"), token))
                .contains("\"box\":0", "\"missCount\":1", "\"lastMiss\":\"et kat\"");
        assertThat(graph(grade, Map.of("id", id, "grade", "MISSED"), token)).contains("\"missCount\":2", "\"lastMiss\":\"et kat\"");
    }

    @Test
    void aPastedListSavesTogetherOrNotAtAll() {
        String token = learner("da");
        String many = "mutation($cards:[EchoCardInput!]!){saveEchoCards(cards:$cards){front back box due}}";
        assertThat(graph(many, Map.of("cards", java.util.List.of(Map.of("front", "hej", "back", "hello"),
                Map.of("front", "tak", "back", "thank you"))), token)).contains("\"front\":\"hej\"", "\"front\":\"tak\"", "\"box\":0");
        assertThat(postGraph(many, Map.of("cards", java.util.List.of(Map.of("front", "farvel", "back", "goodbye"),
                Map.of("front", "  ", "back", "empty"))), token)).contains("Line 2: write the phrase");
        assertThat(graph("query { echoCards { front } }", Map.of(), token)).doesNotContain("farvel");
    }

    @Test
    void emptyOrOverlongSidesAreRejected() {
        String token = learner("de");
        assertThat(postGraph(SAVE, Map.of("front", "  ", "back", "Hello"), token)).contains("Write the phrase in 1–300 characters.");
        assertThat(postGraph(SAVE, Map.of("front", "Hallo", "back", "x".repeat(301)), token))
                .contains("Write what the phrase means in 1–300 characters.");
    }

    private String learner(String languageCode) {
        var body = http.postForObject("/api/v1/auth/register", Map.of("name", "Card Learner",
                "email", "cards-" + UUID.randomUUID() + "@example.test", "password", "A-good-demo-password-2026"), Map.class);
        String token = (String) body.get("token");
        graph("mutation($code:String!){startEcho(goal:\"Talk with friends\",languageCode:$code,selfReportedLevel:\"new\"){onboarded}}",
                Map.of("code", languageCode), token);
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
        headers.setBearerAuth(token);
        return http.postForObject("/graphql", new HttpEntity<>(Map.of("query", query, "variables", variables), headers), String.class);
    }
}
