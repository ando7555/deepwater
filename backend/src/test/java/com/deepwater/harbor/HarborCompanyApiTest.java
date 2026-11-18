package com.deepwater.harbor;

import static org.assertj.core.api.Assertions.assertThat;

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

/** Its own database: once real company postings exist, the sample postings step aside. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:harbor-companies;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "deepwater.harbor.logo-url=http://127.0.0.1:9/unused?domain="})
@AutoConfigureTestRestTemplate
class HarborCompanyApiTest {
    @Autowired TestRestTemplate http;
    @Autowired JobCatalogService catalog;

    @Test
    void realPostingsReplaceSamplesAndCarryCompanyAndHiringProcess() {
        String token = register();
        assertThat(get("/api/v1/harbor/jobs", token)).contains("\"source\":\"SAMPLE\"");

        catalog.upsert(new Job("gh-1", "COMPANY", "Backend Engineer", "Databricks", "DK", "Copenhagen", false, "UNKNOWN",
                "Java.\nInterview Process\nRecruiter Screen → Hiring Manager Screen → Live Coding", List.of(), "https://example.test/1", null, "databricks"));
        catalog.upsert(new Job("gh-2", "COMPANY", "Data Engineer", "Databricks", "SE", "Stockholm", false, "UNKNOWN",
                "Spark.", List.of(), "https://example.test/2", null, "databricks"));

        String jobs = get("/api/v1/harbor/jobs", token);
        assertThat(jobs).contains("\"companyId\":\"databricks\"", "\"hiringProcess\":{\"steps\":[{\"name\":\"Recruiter Screen\"", "\"interviews\":2",
                "\"jobsByCompany\":{\"databricks\":2}").doesNotContain("\"source\":\"SAMPLE\"");
        assertThat(get("/api/v1/harbor/jobs?source=SAMPLE", token)).contains("\"source\":\"SAMPLE\"");
        assertThat(get("/api/v1/harbor/jobs?company=lego", token)).contains("\"jobs\":[]");

        String companies = get("/api/v1/harbor/companies", token);
        assertThat(companies).contains("\"id\":\"databricks\"", "\"jobs\":2", "\"name\":\"Arla Foods\"", "\"connected\":false");

        // Removing what a board no longer lists keeps postings someone tracks.
        http.exchange("/api/v1/harbor/applications/gh-2", HttpMethod.PUT, new HttpEntity<>(Map.of("status", "SAVED"), headers(token)), String.class);
        catalog.removeOthers("databricks", List.of());
        assertThat(catalog.countForCompany("databricks")).isEqualTo(1);
        assertThat(catalog.find("gh-2")).isNotNull();

        // Logos are public but only for known companies; with no reachable icon service the screen falls back to initials.
        assertThat(http.getForEntity("/api/v1/harbor/companies/not-a-company/logo", byte[].class).getStatusCode().value()).isEqualTo(404);
        assertThat(http.getForEntity("/api/v1/harbor/companies/lego/logo", byte[].class).getStatusCode().value()).isEqualTo(404);
    }

    private String register() {
        var body = http.postForObject("/api/v1/auth/register", Map.of("name", "Harbor Tester",
                "email", "harbor-co-" + UUID.randomUUID() + "@example.test", "password", "A-good-demo-password-2026"), Map.class);
        return (String) body.get("token");
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private String get(String path, String token) {
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(token)), String.class).getBody();
    }
}
