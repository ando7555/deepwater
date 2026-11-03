package com.deepwater.harbor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class HarborApiTest {
    private static final String PASSWORD = "A-good-demo-password-2026";
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate db;

    @Test
    void cvUploadBecomesAProfileThatFindsMatchingJobsWithVisaFilters() {
        String token = register();
        var profile = upload(token, "ana-cv.txt", HarborRulesTest.CV.getBytes());
        assertThat(profile.getStatusCode().value()).isEqualTo(200);
        assertThat(profile.getBody()).contains("\"headline\":\"Senior Backend Engineer\"", "\"yearsExperience\":8", "\"hasCv\":true",
                "\"cvFileName\":\"ana-cv.txt\"", "\"Kafka\"", "\"cvCheck\":{\"score\":", "\"id\":\"sections\"")
                .doesNotContain("cvText", "ana@example.test", "\"id\":\"fonts\"");

        var edited = send(HttpMethod.PUT, "/api/v1/harbor/profile", token, Map.of(
                "headline", "Senior Backend Engineer", "skills", List.of("Java", "Spring", "Kafka", "PostgreSQL", "Kubernetes"),
                "yearsExperience", 8, "languages", List.of("English"), "citizenship", "OTHER",
                "targetCountries", List.of("dk", "SE", "NO", "CH"), "remotePreference", "ANY"));
        assertThat(edited.getBody()).contains("\"citizenship\":\"OTHER\"", "\"targetCountries\":[\"DK\",\"SE\",\"NO\",\"CH\"]", "\"hasCv\":true");

        String all = get("/api/v1/harbor/jobs", token);
        assertThat(all).contains("\"profileIsEmpty\":false", "Nordhavn Payments (sample)", "\"source\":\"SAMPLE\"");
        // The best match comes first: the Copenhagen Java/Spring/Kafka role.
        assertThat(all.indexOf("sample-dk-01")).isLessThan(all.indexOf("sample-dk-04"));

        // Someone outside the EU/EEA sees only roles they could take: sponsorship offered, or no permit needed.
        String open = get("/api/v1/harbor/jobs?visa=OPEN_TO_ME&country=DK,SE,NO,CH", token);
        assertThat(open).contains("sample-dk-01", "sample-se-01", "sample-no-01", "sample-ch-01", "sample-no-03")
                .doesNotContain("sample-dk-02", "sample-ch-02", "sample-se-02", "sample-de-01", "\"workPermit\":\"NOT_NEEDED\"");

        String ruledOut = get("/api/v1/harbor/jobs?visa=NOT_RULED_OUT", token);
        assertThat(ruledOut).contains("sample-se-02").doesNotContain("sample-dk-02", "sample-ie-01");
        assertThat(get("/api/v1/harbor/jobs?remote=REMOTE&q=java", token)).contains("sample-eu-01").doesNotContain("sample-se-03", "sample-dk-01");
        assertThat(get("/api/v1/harbor/jobs?minScore=101", token)).contains("\"jobs\":[]");
    }

    @Test
    void anEuCitizenNeedsNoPermitInTheNordics() {
        String token = register();
        send(HttpMethod.PUT, "/api/v1/harbor/profile", token, Map.of("skills", List.of("Java"), "citizenship", "EU_EEA"));
        String result = get("/api/v1/harbor/jobs?visa=NO_PERMIT_NEEDED", token);
        assertThat(result).contains("sample-dk-02", "sample-ch-02", "\"workPermit\":\"NOT_NEEDED\"").doesNotContain("sample-eu-01");
    }

    @Test
    void pdfCvIsRead() throws Exception {
        String token = register();
        var response = upload(token, "cv.pdf", pdf("Data Engineer", "I have 5 years with Python, Spark and Airflow."));
        assertThat(response.getBody()).contains("\"id\":\"fonts\"", "\"id\":\"pages\"", "\"id\":\"readable\"");
        assertThat(response.getBody()).contains("\"headline\":\"Data Engineer\"", "\"Python\"", "\"Spark\"", "\"Airflow\"", "\"yearsExperience\":5");
        assertThat(upload(token, "cv.docx", new byte[]{1, 2, 3}).getBody()).contains("Upload a PDF or .txt file");
        assertThat(upload(token, "cv.pdf", "not really a pdf".getBytes()).getBody()).contains("This PDF could not be read");
    }

    @Test
    void applicationsAreTrackedPrivatelyAndDeletedWithTheAccount() {
        String token = register();
        var saved = send(HttpMethod.PUT, "/api/v1/harbor/applications/sample-dk-01", token, Map.of("status", "APPLIED", "note", "Sent CV on Monday"));
        assertThat(saved.getBody()).contains("\"status\":\"APPLIED\"", "Sent CV on Monday", "Nordhavn Payments");
        send(HttpMethod.PUT, "/api/v1/harbor/applications/sample-dk-01", token, Map.of("status", "INTERVIEW"));
        assertThat(get("/api/v1/harbor/applications", token)).contains("\"status\":\"INTERVIEW\"", "Sent CV on Monday");
        assertThat(get("/api/v1/harbor/jobs?q=Nordhavn", token)).contains("\"applicationStatus\":\"INTERVIEW\"");
        assertThat(send(HttpMethod.PUT, "/api/v1/harbor/applications/missing-job", token, Map.of()).getStatusCode().value()).isEqualTo(404);
        assertThat(send(HttpMethod.PUT, "/api/v1/harbor/applications/sample-dk-01", token, Map.of("status", "HIRED")).getStatusCode().value()).isEqualTo(400);

        String other = register();
        assertThat(get("/api/v1/harbor/applications", other)).isEqualTo("[]");
        assertThat(http.getForEntity("/api/v1/harbor/applications", String.class).getStatusCode().value()).isEqualTo(401);

        upload(token, "cv.txt", HarborRulesTest.CV.getBytes());
        String email = db.queryForObject("select a.email from accounts a join user_sessions s on s.account_id=a.id where s.token=?", String.class, token);
        String accountId = db.queryForObject("select account_id from user_sessions where token=?", String.class, token);
        assertThat(send(HttpMethod.DELETE, "/api/v1/auth/account", token, Map.of("confirmEmail", email)).getStatusCode().value()).isEqualTo(204);
        assertThat(db.queryForObject("select count(*) from harbor_profiles where account_id=?", Integer.class, accountId)).isZero();
        assertThat(db.queryForObject("select count(*) from harbor_applications where account_id=?", Integer.class, accountId)).isZero();
    }

    @Test
    void cvCanBeForgottenWhileTheProfileStays() {
        String token = register();
        upload(token, "cv.txt", HarborRulesTest.CV.getBytes());
        var after = send(HttpMethod.DELETE, "/api/v1/harbor/cv", token, null);
        assertThat(after.getBody()).contains("\"hasCv\":false", "\"cvFileName\":null", "\"cvCheck\":null", "\"headline\":\"Senior Backend Engineer\"");
        assertThat(db.queryForObject("select count(*) from harbor_profiles where cv_text is not null and account_id=" +
                "(select account_id from user_sessions where token=?)", Integer.class, token)).isZero();
    }

    @Test
    void liveSourceIsOffByDefaultAndSaysSo() {
        String token = register();
        assertThat(send(HttpMethod.POST, "/api/v1/harbor/jobs/refresh", token, null).getBody())
                .contains("\"enabled\":false", "Live postings are turned off");
        assertThat(get("/api/v1/harbor/sources", token)).contains("\"liveEnabled\":false", "\"sampleJobs\":18", "nyidanmark.dk", "sem.admin.ch");
    }

    @Test
    void profileInputIsValidated() {
        String token = register();
        assertThat(send(HttpMethod.PUT, "/api/v1/harbor/profile", token, Map.of("citizenship", "MARS")).getBody()).contains("Choose EU/EEA");
        assertThat(send(HttpMethod.PUT, "/api/v1/harbor/profile", token, Map.of("targetCountries", List.of("Denmark"))).getBody()).contains("Keep lists short");
        assertThat(send(HttpMethod.PUT, "/api/v1/harbor/profile", token, Map.of("targetCountries", List.of("D1"))).getBody()).contains("two-letter");
        assertThat(send(HttpMethod.PUT, "/api/v1/harbor/profile", token, Map.of("yearsExperience", 99)).getBody()).contains("between 0 and 60");
    }

    private String register() {
        var body = http.postForObject("/api/v1/auth/register", Map.of("name", "Harbor Tester",
                "email", "harbor-" + UUID.randomUUID() + "@example.test", "password", PASSWORD), Map.class);
        return (String) body.get("token");
    }

    private org.springframework.http.ResponseEntity<String> upload(String token, String name, byte[] bytes) {
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("file", new ByteArrayResource(bytes) { @Override public String getFilename() { return name; } });
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return http.exchange("/api/v1/harbor/cv", HttpMethod.POST, new HttpEntity<>(parts, headers), String.class);
    }

    private String get(String path, String token) {
        return send(HttpMethod.GET, path, token, null).getBody();
    }

    private org.springframework.http.ResponseEntity<String> send(HttpMethod method, String path, String token, Object body) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private static byte[] pdf(String... lines) throws Exception {
        try (var document = new PDDocument(); var out = new ByteArrayOutputStream()) {
            var page = new PDPage();
            document.addPage(page);
            try (var content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                for (String line : lines) { content.showText(line); content.newLineAtOffset(0, -16); }
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
