package com.deepwater.harbor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Live postings from the public Arbeitnow job board API (no key needed; mostly Germany and Europe).
 * It is off unless HARBOR_ARBEITNOW_ENABLED=true, and refreshes at most once every ten minutes for
 * the whole server, so a busy page cannot flood the source. Each posting links back to the original.
 */
@Service
public class ArbeitnowJobSource {
    static final String SOURCE = "ARBEITNOW";
    private static final Logger log = LoggerFactory.getLogger(ArbeitnowJobSource.class);
    private static final Duration MIN_GAP = Duration.ofMinutes(10);
    private static final int PAGES = 3;

    private final boolean enabled;
    private final String baseUrl;
    private final JobCatalogService catalog;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private volatile Instant lastRefresh;

    public ArbeitnowJobSource(@Value("${deepwater.harbor.arbeitnow.enabled:false}") boolean enabled,
                              @Value("${deepwater.harbor.arbeitnow.base-url:https://www.arbeitnow.com/api/job-board-api}") String baseUrl,
                              JobCatalogService catalog) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.catalog = catalog;
    }

    public boolean enabled() { return enabled; }

    public Instant lastRefresh() { return lastRefresh; }

    public synchronized Refresh refresh() {
        if (!enabled) return new Refresh(false, 0, "Live postings are turned off on this server. Showing sample postings only.", lastRefresh);
        if (lastRefresh != null && lastRefresh.plus(MIN_GAP).isAfter(Instant.now()))
            return new Refresh(true, 0, "Live postings were refreshed less than ten minutes ago.", lastRefresh);
        int saved = 0;
        try {
            for (int page = 1; page <= PAGES; page++) {
                var response = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "?page=" + page))
                        .timeout(Duration.ofSeconds(20)).header("Accept", "application/json").GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) throw new IllegalStateException("status " + response.statusCode());
                List<Job> jobs = parse(response.body());
                if (jobs.isEmpty()) break;
                for (Job job : jobs) { catalog.upsert(job); saved++; }
            }
        } catch (Exception ex) {
            log.warn("Arbeitnow refresh failed after {} postings: {}", saved, ex.getMessage());
            lastRefresh = Instant.now();
            return new Refresh(true, saved, "The live job source did not answer. " + saved + " postings were updated.", lastRefresh);
        }
        lastRefresh = Instant.now();
        return new Refresh(true, saved, saved + " live postings updated.", lastRefresh);
    }

    /** Maps one page of the API's JSON ({"data":[{slug, company_name, title, description, remote, url, tags, location, created_at}]}). */
    List<Job> parse(String body) {
        JsonNode root = json.readTree(body);
        List<Job> jobs = new ArrayList<>();
        for (JsonNode node : root.path("data")) {
            String slug = node.path("slug").asString("");
            String title = node.path("title").asString("");
            String url = node.path("url").asString("");
            if (slug.isBlank() || title.isBlank() || !url.startsWith("https://")) continue;
            String description = plainText(node.path("description").asString(""));
            String location = node.path("location").asString("");
            List<String> tags = new ArrayList<>();
            node.path("tags").forEach(tag -> tags.add(tag.asString("")));
            Boolean flag = node.has("visa_sponsorship") ? node.path("visa_sponsorship").asBoolean(false) : null;
            long created = node.path("created_at").asLong(0);
            jobs.add(new Job("arbeitnow-" + slug, SOURCE, title, node.path("company_name").asString("Unknown company"),
                    Countries.fromLocation(location), location, node.path("remote").asBoolean(false),
                    VisaSignals.read(title + "\n" + description, flag), description, tags, url,
                    created > 0 ? Instant.ofEpochSecond(created) : null));
        }
        return jobs;
    }

    /** Postings arrive as HTML; Harbor shows and matches plain text only. */
    static String plainText(String html) {
        return html.replaceAll("(?i)<br\\s*/?>|</p>|</li>|</h\\d>", "\n").replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                .replaceAll("[ \\t]+", " ").replaceAll(" *\\n *", "\n").replaceAll("\\n{3,}", "\n\n").strip();
    }

    public record Refresh(boolean enabled, int updated, String message, Instant lastRefreshedAt) {}
}
