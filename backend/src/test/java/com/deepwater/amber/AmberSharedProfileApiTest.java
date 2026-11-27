package com.deepwater.amber;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Amber on the one Deepwater profile (owner ask 2026-11-27): photos, age, bio, the languages a person knows,
 * what they learn in Echo, whether they use Harbor, and ending a conversation kindly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AmberSharedProfileApiTest {
    private static final String PASSWORD = "A-good-demo-password-2026";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String UPDATE = "mutation($input:PersonProfileInput!){updateMyProfile(input:$input){publicName age bio city languages{language level}}}";
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate db;

    @Test
    void joiningAmberFillsTheOneDeepwaterProfileAndAmberReadsFromIt() throws Exception {
        String area = uniqueArea();
        String anna = register();
        var join = amberForm("Anna", area, List.of("DATING"));
        join.remove("age");
        join.put("birthDate", "1995-03-14");
        join.put("about", "Coffee, long walks, bad puns.");
        assertThat(send(HttpMethod.PUT, "/api/v1/amber/profile", anna, join).getStatusCode().value()).isEqualTo(200);

        String profile = graph("query { myProfile { publicName birthDate age city bio } }", Map.of(), anna);
        assertThat(profile).contains("\"publicName\":\"Anna\"", "\"birthDate\":\"1995-03-14\"", "\"city\":\"" + area + "\"",
                "\"bio\":\"Coffee, long walks, bad puns.\"");

        // Editing the Deepwater profile changes what Amber shows: there is only one profile.
        graph(UPDATE, Map.of("input", Map.of("name", "Anna Private Surname", "publicName", "Annie", "birthDate", "1995-03-14",
                "city", area, "bio", "New bio")), anna);
        String mia = join("Mia", area, List.of("DATING"));
        JsonNode card = person(mia, "Annie");
        assertThat(card).isNotNull();
        assertThat(card.get("about").asText()).isEqualTo("New bio");
        assertThat(card.get("age").asInt()).isBetween(29, 32);
        assertThat(card.toString()).doesNotContain("Private Surname", "@example.test");

        // A birth date under 18 keeps a person out of Amber.
        String young = register();
        var tooYoung = amberForm("Kid", area, List.of("DATING"));
        tooYoung.remove("age");
        tooYoung.put("birthDate", java.time.LocalDate.now().minusYears(17).toString());
        assertThat(send(HttpMethod.PUT, "/api/v1/amber/profile", young, tooYoung).getStatusCode().value()).isEqualTo(400);
        assertThat(db.queryForObject("select count(*) from amber_profiles a join user_sessions s on s.account_id=a.account_id where s.token=?",
                Integer.class, young)).isZero();
    }

    @Test
    void photosOpenOnlyAfterBothSayYesUnlessThePersonShowsThemToEveryone() throws Exception {
        String area = uniqueArea();
        String anna = join("Anna", area, List.of("DATING"));
        String mia = join("Mia", area, List.of("DATING"));
        String photoId = json(upload(mia, jpegWithGpsExif())).get("id").asText();

        // The stored photo is a fresh JPEG: the EXIF block with the GPS position is gone.
        byte[] own = http.exchange("/api/v1/account/photos/" + photoId, HttpMethod.GET, new HttpEntity<>(headers(mia)), byte[].class).getBody();
        assertThat(own).isNotNull();
        assertThat(own[0] & 0xff).isEqualTo(0xFF);
        assertThat(own[1] & 0xff).isEqualTo(0xD8);
        String text = new String(own, StandardCharsets.ISO_8859_1);
        assertThat(text).doesNotContain("Exif", "GPS-55.6761N");

        String miaMember = memberId(mia);
        JsonNode card = person(anna, "Mia");
        assertThat(card.get("photoIds")).isEmpty();
        assertThat(card.get("photoCount").asInt()).isEqualTo(1);
        assertThat(card.get("photosAfterYes").asBoolean()).isTrue();
        assertThat(photoStatus(anna, miaMember, photoId)).isEqualTo(404);

        String invitation = invite(anna, miaMember);
        // A pending invitation is not "both said yes".
        assertThat(photoStatus(anna, miaMember, photoId)).isEqualTo(404);
        send(HttpMethod.POST, "/api/v1/amber/invitations/" + invitation + "/accept", mia, null);
        assertThat(photoStatus(anna, miaMember, photoId)).isEqualTo(200);
        JsonNode connection = json(send(HttpMethod.GET, "/api/v1/amber/connections", anna, null)).get(0).get("person");
        assertThat(connection.get("photoIds").get(0).asText()).isEqualTo(photoId);

        // A block closes the photos again.
        send(HttpMethod.POST, "/api/v1/amber/safety/block", mia, Map.of("memberId", memberId(anna)));
        assertThat(photoStatus(anna, miaMember, photoId)).isEqualTo(404);

        // Someone who chooses "everyone in Amber" is seen with photos in Discover.
        String ola = register();
        var olaForm = amberForm("Ola", area, List.of("DATING"));
        olaForm.put("photosVisibility", "MEMBERS");
        send(HttpMethod.PUT, "/api/v1/amber/profile", ola, olaForm);
        String olaPhoto = json(upload(ola, png(400, 300))).get("id").asText();
        assertThat(person(anna, "Ola").get("photoIds").get(0).asText()).isEqualTo(olaPhoto);
        assertThat(photoStatus(anna, memberId(ola), olaPhoto)).isEqualTo(200);
        var wrongChoice = amberForm("Ola", area, List.of("DATING"));
        wrongChoice.put("photosVisibility", "PUBLIC_WEB");
        assertThat(send(HttpMethod.PUT, "/api/v1/amber/profile", ola, wrongChoice).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void photoUploadsAreCheckedAndLimitedToFour() throws Exception {
        String anna = register();
        assertThat(upload(anna, "not an image".getBytes(StandardCharsets.UTF_8)).getStatusCode().value()).isEqualTo(400);
        assertThat(upload(anna, png(120, 120)).getStatusCode().value()).isEqualTo(400); // too small to be a useful photo
        String first = null;
        for (int i = 0; i < 4; i++) {
            var saved = upload(anna, png(300 + i, 300));
            assertThat(saved.getStatusCode().value()).as(saved.getBody()).isEqualTo(200);
            if (first == null) first = json(saved).get("id").asText();
        }
        assertThat(upload(anna, png(300, 300)).getStatusCode().value()).isEqualTo(400);
        JsonNode list = json(send(HttpMethod.GET, "/api/v1/account/photos", anna, null));
        String last = list.get(3).get("id").asText();
        send(HttpMethod.POST, "/api/v1/account/photos/" + last + "/first", anna, null);
        assertThat(json(send(HttpMethod.GET, "/api/v1/account/photos", anna, null)).get(0).get("id").asText()).isEqualTo(last);
        assertThat(send(HttpMethod.DELETE, "/api/v1/account/photos/" + first, anna, null).getStatusCode().value()).isEqualTo(204);
        assertThat(json(send(HttpMethod.GET, "/api/v1/account/photos", anna, null))).hasSize(3);
        // Someone else's photo cannot be deleted or read through the owner route.
        String other = register();
        assertThat(send(HttpMethod.DELETE, "/api/v1/account/photos/" + last, other, null).getStatusCode().value()).isEqualTo(404);
        assertThat(send(HttpMethod.GET, "/api/v1/account/photos/" + last, other, null).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void languagesEchoLevelsAndHarborAreShownOnlyWhenSwitchedOnAndLanguageExchangeIsAReason() throws Exception {
        String area = uniqueArea();
        String anna = join("Anna", area, List.of("FRIENDS"));
        String mia = join("Mia", area, List.of("FRIENDS"));
        String annaAccount = account(anna);
        String miaAccount = account(mia);
        // Anna learns Danish in Echo and took the Polish practice exam; Mia speaks Danish and uses Harbor.
        db.update("insert into echo_learner_profiles(account_id,language_code,goal,self_reported_level,diagnostic_score,starting_route) " +
                "values(?,'da','Find a job','some',0,'GUIDED')", annaAccount);
        db.update("insert into exam_attempts(id,account_id,exam_id,eligible,level_estimate,average_percent,result) values(?,?,'polish-b1',true,'B1',70,'{}')",
                UUID.randomUUID().toString(), annaAccount);
        graph(UPDATE, Map.of("input", Map.of("name", "Mia Tester", "publicName", "Mia", "city", area,
                "languages", List.of(Map.of("language", "Danish", "level", "NATIVE"), Map.of("language", "English", "level", "FLUENT"),
                        Map.of("language", "danish", "level", "BASIC")))), mia);
        graph(UPDATE, Map.of("input", Map.of("name", "Anna Tester", "publicName", "Anna", "city", area,
                "languages", List.of(Map.of("language", "Polish", "level", "FLUENT")))), anna);
        db.update("insert into harbor_profiles(account_id) values(?)", miaAccount);

        JsonNode miaSeenByAnna = suggestion(anna, "Mia");
        // Languages are on by default; duplicates are dropped. Anna's own Echo language makes the exchange a reason for her.
        assertThat(miaSeenByAnna.get("person").get("languages").toString()).contains("Danish", "NATIVE", "English").doesNotContain("BASIC");
        assertThat(miaSeenByAnna.get("reasons").toString()).contains("Speaks Danish, which you are learning");
        assertThat(miaSeenByAnna.get("person").get("usesHarbor").isNull()).isTrue();

        // Mia does not learn that Anna learns Danish or Polish until Anna switches it on.
        JsonNode annaSeenByMia = suggestion(mia, "Anna");
        assertThat(annaSeenByMia.get("reasons").toString()).doesNotContain("learning");
        assertThat(annaSeenByMia.get("person").get("learningDetails")).isEmpty();
        assertThat(annaSeenByMia.get("person").get("learning").isNull()).isTrue();
        assertThat(annaSeenByMia.toString()).doesNotContain("B1", "own estimate").contains("Polish");

        var annaSharing = amberForm("Anna", area, List.of("FRIENDS"));
        annaSharing.put("shareLearning", true);
        send(HttpMethod.PUT, "/api/v1/amber/profile", anna, annaSharing);
        annaSeenByMia = suggestion(mia, "Anna");
        assertThat(annaSeenByMia.get("person").get("learningDetails").toString())
                .contains("Danish", "knows some (own estimate)", "Polish", "B1 on a Deepwater practice exam");
        assertThat(annaSeenByMia.get("reasons").toString()).contains("Is learning Danish, which you speak");

        var miaSharing = amberForm("Mia", area, List.of("FRIENDS"));
        miaSharing.put("shareHarbor", true);
        miaSharing.put("shareLanguages", false);
        send(HttpMethod.PUT, "/api/v1/amber/profile", mia, miaSharing);
        miaSeenByAnna = suggestion(anna, "Mia");
        assertThat(miaSeenByAnna.get("person").get("usesHarbor").asBoolean()).isTrue();
        assertThat(miaSeenByAnna.get("person").get("languages")).isEmpty();
        assertThat(miaSeenByAnna.get("reasons").toString()).doesNotContain("Speaks Danish");

        JsonNode own = json(send(HttpMethod.GET, "/api/v1/amber/profile", mia, null));
        assertThat(own.get("usesHarbor").asBoolean()).isTrue();
        assertThat(own.get("shareLanguages").asBoolean()).isFalse();
    }

    @Test
    void aConversationCanBeClosedWithAKindNoteInsteadOfGhosting() throws Exception {
        String area = uniqueArea();
        String anna = join("Anna", area, List.of("DATING"));
        String mia = join("Mia", area, List.of("DATING"));
        String invitation = invite(anna, memberId(mia));
        send(HttpMethod.POST, "/api/v1/amber/invitations/" + invitation + "/accept", mia, null);
        JsonNode before = json(send(HttpMethod.GET, "/api/v1/amber/connections", mia, null)).get(0);
        assertThat(before.get("lastMessageMine").isNull()).isTrue();
        send(HttpMethod.POST, "/api/v1/amber/connections/" + invitation + "/messages", anna, Map.of("body", "Hi Mia!"));
        assertThat(json(send(HttpMethod.GET, "/api/v1/amber/connections", mia, null)).get(0).get("lastMessageMine").asBoolean()).isFalse();
        assertThat(json(send(HttpMethod.GET, "/api/v1/amber/connections", anna, null)).get(0).get("lastMessageMine").asBoolean()).isTrue();

        assertThat(send(HttpMethod.POST, "/api/v1/amber/connections/" + invitation + "/leave", mia, Map.of("note", "You are ugly"))
                .getStatusCode().value()).isEqualTo(400);
        assertThat(send(HttpMethod.POST, "/api/v1/amber/connections/" + invitation + "/leave", mia, Map.of("note", "NOT_A_MATCH"))
                .getStatusCode().value()).isEqualTo(204);
        JsonNode closed = json(send(HttpMethod.GET, "/api/v1/amber/connections/closed", anna, null));
        assertThat(closed).hasSize(1);
        assertThat(closed.get(0).get("displayName").asText()).isEqualTo("Mia");
        assertThat(closed.get(0).get("note").asText()).startsWith("Thank you for the conversation.");
        // The person who closed it does not see their own goodbye as a closed conversation.
        assertThat(json(send(HttpMethod.GET, "/api/v1/amber/connections/closed", mia, null))).isEmpty();
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode suggestion(String token, String name) throws Exception {
        var response = send(HttpMethod.GET, "/api/v1/amber/suggestions", token, null);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        for (JsonNode item : json(response)) if (name.equals(item.get("person").get("displayName").asText())) return item;
        throw new AssertionError(name + " not suggested: " + response.getBody());
    }

    private JsonNode person(String token, String name) throws Exception { return suggestion(token, name).get("person"); }

    private int photoStatus(String token, String memberId, String photoId) {
        return http.exchange("/api/v1/amber/members/" + memberId + "/photos/" + photoId, HttpMethod.GET, new HttpEntity<>(headers(token)), byte[].class)
                .getStatusCode().value();
    }

    private ResponseEntity<String> upload(String token, byte[] bytes) {
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("photo", new ByteArrayResource(bytes) { @Override public String getFilename() { return "photo.jpg"; } });
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return http.exchange("/api/v1/account/photos", HttpMethod.POST, new HttpEntity<>(parts, headers), String.class);
    }

    static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setColor(new Color(30, 90, 160, 200));
        g.fillOval(10, 10, width - 20, height - 20);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** A real JPEG with an APP1 "Exif" segment holding a fake GPS position, as a phone would write it. */
    static byte[] jpegWithGpsExif() throws Exception {
        BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, 640, 480);
        g.dispose();
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", jpeg);
        byte[] plain = jpeg.toByteArray();
        byte[] payload = "Exif\0\0GPS-55.6761N-12.5683E".getBytes(StandardCharsets.ISO_8859_1);
        int length = payload.length + 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(plain, 0, 2); // SOI
        out.write(new byte[]{(byte) 0xFF, (byte) 0xE1, (byte) (length >> 8), (byte) length});
        out.write(payload);
        out.write(plain, 2, plain.length - 2);
        return out.toByteArray();
    }

    private String join(String name, String area, List<String> intents) {
        String token = register();
        var saved = send(HttpMethod.PUT, "/api/v1/amber/profile", token, amberForm(name, area, intents));
        assertThat(saved.getStatusCode().value()).as(saved.getBody()).isEqualTo(200);
        return token;
    }

    private static Map<String, Object> amberForm(String name, String area, List<String> intents) {
        var body = new HashMap<String, Object>();
        body.put("displayName", name);
        body.put("age", 30);
        body.put("confirmAdult", true);
        body.put("area", area);
        body.put("intents", intents);
        body.put("interests", List.of());
        return body;
    }

    private String invite(String token, String memberId) throws Exception {
        var response = send(HttpMethod.POST, "/api/v1/amber/invitations", token, Map.of("memberId", memberId));
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        return json(response).get("id").asText();
    }

    private String memberId(String token) throws Exception {
        return json(send(HttpMethod.GET, "/api/v1/amber/profile", token, null)).get("memberId").asText();
    }

    private String account(String token) {
        return db.queryForObject("select account_id from user_sessions where token=?", String.class, token);
    }

    private static String uniqueArea() { return "Town-" + UUID.randomUUID().toString().substring(0, 8); }

    private String register() {
        var body = http.postForObject("/api/v1/auth/register", Map.of("name", "Amber Tester",
                "email", "amber-" + UUID.randomUUID() + "@example.test", "password", PASSWORD), Map.class);
        return (String) body.get("token");
    }

    private String graph(String query, Map<String, ?> variables, String token) {
        String result = http.postForObject("/graphql", new HttpEntity<>(Map.of("query", query, "variables", variables), headers(token)), String.class);
        assertThat(result).doesNotContain("\"errors\"");
        return result;
    }

    private static JsonNode json(ResponseEntity<String> response) throws Exception { return JSON.readTree(response.getBody()); }

    private static HttpHeaders headers(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<String> send(HttpMethod method, String path, String token, Object body) {
        return http.exchange(path, method, new HttpEntity<>(body, headers(token)), String.class);
    }
}
