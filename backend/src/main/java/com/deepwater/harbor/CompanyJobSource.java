package com.deepwater.harbor;

import com.deepwater.harbor.CompanyRegistry.Board;
import com.deepwater.harbor.CompanyRegistry.Company;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Real postings straight from each employer's own public job board: Greenhouse, Lever, Ashby,
 * SmartRecruiters and Workday all publish the jobs a company lists on its careers site as JSON, without a key.
 * Only roles in the countries Harbor covers are kept, at most {@value #PER_COMPANY} per employer, newest first.
 * The whole set refreshes in the background a minute after start and then every six hours.
 */
@Service
public class CompanyJobSource {
    static final String SOURCE = "COMPANY";
    static final Set<String> COUNTRIES = Set.of("DK", "SE", "NO", "FI", "IS", "DE", "CH", "AT", "NL");
    static final int PER_COMPANY = 60;
    /** Workday and SmartRecruiters need one extra request per posting for its text, so they get fewer. */
    static final int PER_COMPANY_DETAILED = 30;
    private static final Duration MIN_GAP = Duration.ofMinutes(30);
    private static final String LOCATION = "location";
    private static final String CONTENT = "content";
    private static final String TITLE = "title";
    private static final String REMOTE = "remote";
    private static final String CATEGORIES = "categories";
    private static final String COUNTRY = "country";
    private static final Logger log = LoggerFactory.getLogger(CompanyJobSource.class);

    private final boolean enabled;
    private final CompanyRegistry registry;
    private final JobCatalogService catalog;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final AtomicBoolean running = new AtomicBoolean();
    private final Map<String, CompanyStatus> statuses = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile Instant lastRefresh;
    private ScheduledExecutorService scheduler;

    public CompanyJobSource(@Value("${deepwater.harbor.companies.enabled:true}") boolean enabled,
                            CompanyRegistry registry, JobCatalogService catalog) {
        this.enabled = enabled;
        this.registry = registry;
        this.catalog = catalog;
    }

    public boolean enabled() { return enabled; }

    public Instant lastRefresh() { return lastRefresh; }

    public boolean running() { return running.get(); }

    public CompanyStatus status(String companyId) { return statuses.get(companyId); }

    @EventListener(ApplicationReadyEvent.class)
    void schedule() {
        if (!enabled) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("harbor-company-jobs").factory());
        scheduler.scheduleWithFixedDelay(this::refreshAll, 60, 6L * 60 * 60, TimeUnit.SECONDS);
    }

    /** Starts a refresh in the background unless one is running or the last one was under half an hour ago. */
    public String requestRefresh() {
        if (!enabled) return "Company job boards are turned off on this server.";
        if (running.get()) return "Harbor is already fetching the newest company postings.";
        if (lastRefresh != null && lastRefresh.plus(MIN_GAP).isAfter(Instant.now())) return "Company postings were refreshed less than 30 minutes ago.";
        Thread.ofVirtual().name("harbor-company-jobs-now").start(this::refreshAll);
        return "Fetching the newest company postings. This takes a minute or two.";
    }

    void refreshAll() {
        if (!running.compareAndSet(false, true)) return;
        try {
            for (Company company : registry.all()) {
                if (!company.connected()) continue;
                try {
                    List<Job> jobs = fetch(company);
                    for (Job job : jobs) catalog.upsert(job);
                    catalog.removeOthers(company.id(), jobs.stream().map(Job::id).toList());
                    statuses.put(company.id(), new CompanyStatus(jobs.size(), Instant.now(), null));
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (IOException | RuntimeException ex) {
                    log.warn("Harbor could not read the {} job board: {}", company.name(), ex.toString());
                    statuses.put(company.id(), new CompanyStatus(catalog.countForCompany(company.id()), Instant.now(), "The job board did not answer."));
                }
            }
            lastRefresh = Instant.now();
        } finally {
            running.set(false);
        }
    }

    List<Job> fetch(Company company) throws IOException, InterruptedException {
        Board board = company.board();
        List<Job> jobs = switch (board.type()) {
            case "GREENHOUSE" -> parseGreenhouse(company, get("https://boards-api.greenhouse.io/v1/boards/" + path(board.id()) + "/jobs?content=true"));
            case "LEVER" -> parseLever(company, get("https://api.lever.co/v0/postings/" + path(board.id()) + "?mode=json"));
            case "ASHBY" -> parseAshby(company, get("https://api.ashbyhq.com/posting-api/job-board/" + path(board.id())));
            case "SMARTRECRUITERS" -> smartRecruiters(company);
            case "WORKDAY" -> workday(company);
            default -> List.of();
        };
        return newest(jobs, PER_COMPANY);
    }

    /** Greenhouse: a jobs array; location name, offices, entity-escaped HTML content, first published date and departments. */

    List<Job> parseGreenhouse(Company company, String body) {
        List<Job> jobs = new ArrayList<>();
        for (JsonNode node : json.readTree(body).path("jobs")) {
            String location = node.path(LOCATION).path("name").asString("");
            String country = Countries.fromLocation(location);
            if (country == null) for (JsonNode office : node.path("offices")) {
                country = Countries.fromLocation(office.path(LOCATION).asString(""));
                if (country != null) break;
            }
            List<String> tags = new ArrayList<>();
            node.path("departments").forEach(d -> tags.add(d.path("name").asString("")));
            String description = ArbeitnowJobSource.plainText(unescape(node.path(CONTENT).asString("")));
            add(jobs, company, new Posting("gh-" + node.path("id").asString(""), node.path(TITLE).asString(""), country, location,
                    location.toLowerCase(Locale.ROOT).contains("remote"), description, tags, node.path("absolute_url").asString(""),
                    instant(node.path("first_published").asString(node.path("updated_at").asString("")))));
        }
        return jobs;
    }

    /** Lever: an array of postings with a country code, categories, plain description, lists and the hosted page. */

    List<Job> parseLever(Company company, String body) {
        List<Job> jobs = new ArrayList<>();
        for (JsonNode node : json.readTree(body)) {
            String location = node.path(CATEGORIES).path(LOCATION).asString("");
            String country = node.path(COUNTRY).asString("").toUpperCase(Locale.ROOT);
            if (country.length() != 2) country = Countries.fromLocation(location);
            StringBuilder text = new StringBuilder(node.path("descriptionPlain").asString(""));
            for (JsonNode list : node.path("lists"))
                text.append("\n\n").append(list.path("text").asString("")).append('\n')
                        .append(ArbeitnowJobSource.plainText(list.path(CONTENT).asString("")));
            text.append("\n\n").append(node.path("additionalPlain").asString(""));
            List<String> tags = new ArrayList<>();
            tags.add(node.path(CATEGORIES).path("team").asString(""));
            tags.add(node.path(CATEGORIES).path("commitment").asString(""));
            long created = node.path("createdAt").asLong(0);
            add(jobs, company, new Posting("lever-" + node.path("id").asString(""), node.path("text").asString(""), country, location,
                    REMOTE.equalsIgnoreCase(node.path("workplaceType").asString("")), text.toString().strip(), tags,
                    node.path("hostedUrl").asString(""), created > 0 ? Instant.ofEpochMilli(created) : null));
        }
        return jobs;
    }

    /** Ashby: a jobs array with location, secondary locations, postal address country, plain description and job page. */

    List<Job> parseAshby(Company company, String body) {
        List<Job> jobs = new ArrayList<>();
        for (JsonNode node : json.readTree(body).path("jobs")) {
            if (!node.path("isListed").asBoolean(true)) continue;
            String location = node.path(LOCATION).asString("");
            String country = Countries.fromLocation(node.path("address").path("postalAddress").path("addressCountry").asString(""));
            if (country == null) country = Countries.fromLocation(location);
            if (country == null) for (JsonNode other : node.path("secondaryLocations")) {
                country = Countries.fromLocation(other.path(LOCATION).asString(""));
                if (country != null) break;
            }
            add(jobs, company, new Posting("ashby-" + node.path("id").asString(""), node.path(TITLE).asString(""), country, location,
                    node.path("isRemote").asBoolean(false) && "Remote".equalsIgnoreCase(node.path("workplaceType").asString("")),
                    node.path("descriptionPlain").asString(""),
                    List.of(node.path("department").asString(""), node.path("employmentType").asString("")),
                    node.path("jobUrl").asString(""), instant(node.path("publishedAt").asString(""))));
        }
        return jobs;
    }

    /** SmartRecruiters: list per country, then one request per posting for its text. */

    private List<Job> smartRecruiters(Company company) throws IOException, InterruptedException {
        String base = "https://api.smartrecruiters.com/v1/companies/" + path(company.board().id()) + "/postings";
        List<JsonNode> listed = new ArrayList<>();
        for (String country : COUNTRIES.stream().sorted().toList()) {
            JsonNode page = json.readTree(get(base + "?limit=100&country=" + country.toLowerCase(Locale.ROOT)));
            page.path(CONTENT).forEach(listed::add);
        }
        listed.sort(Comparator.comparing((JsonNode n) -> n.path("releasedDate").asString("")).reversed());
        List<Job> jobs = new ArrayList<>();
        for (JsonNode summary : listed) {
            if (jobs.size() >= PER_COMPANY_DETAILED) break;
            Job job = parseSmartRecruitersPosting(company, get(base + "/" + path(summary.path("id").asString(""))));
            if (job != null) jobs.add(job);
        }
        return jobs;
    }

    Job parseSmartRecruitersPosting(Company company, String body) {
        JsonNode node = json.readTree(body);
        JsonNode location = node.path(LOCATION);
        StringBuilder text = new StringBuilder();
        for (String key : List.of("jobDescription", "qualifications", "additionalInformation")) {
            JsonNode section = node.path("jobAd").path("sections").path(key);
            if (section.path("text").asString("").isBlank()) continue;
            text.append(section.path(TITLE).asString("")).append('\n')
                    .append(ArbeitnowJobSource.plainText(section.path("text").asString(""))).append("\n\n");
        }
        List<Job> one = new ArrayList<>();
        add(one, company, new Posting("sr-" + node.path("id").asString(""), node.path("name").asString(""),
                location.path(COUNTRY).asString("").toUpperCase(Locale.ROOT),
                String.join(", ", List.of(location.path("city").asString(""), Countries.name(location.path(COUNTRY).asString("").toUpperCase(Locale.ROOT)))
                        .stream().filter(s -> s != null && !s.isBlank()).toList()),
                location.path(REMOTE).asBoolean(false), text.toString().strip(),
                List.of(node.path("function").path("label").asString(""), node.path("typeOfEmployment").path("label").asString("")),
                node.path("postingUrl").asString(""), instant(node.path("releasedDate").asString(""))));
        return one.isEmpty() ? null : one.getFirst();
    }

    /** Workday: the careers site's own JSON API. Filter by the country facet, then one request per posting. */

    private List<Job> workday(Company company) throws IOException, InterruptedException {
        Board board = company.board();
        String base = "https://" + board.host() + "/wday/cxs/" + path(board.id()) + "/" + path(board.site());
        JsonNode first = json.readTree(post(base + "/jobs", "{\"limit\":1,\"offset\":0,\"searchText\":\"\",\"appliedFacets\":{}}"));
        Map<String, List<String>> facets = workdayCountryFacet(first);
        List<String> paths = new ArrayList<>();
        for (int offset = 0; paths.size() < PER_COMPANY_DETAILED && offset < 200; offset += 20) {
            String request = json.writeValueAsString(Map.of("limit", 20, "offset", offset, "searchText", "", "appliedFacets", facets));
            JsonNode page = json.readTree(post(base + "/jobs", request));
            List<String> found = new ArrayList<>();
            page.path("jobPostings").forEach(p -> found.add(p.path("externalPath").asString("")));
            if (found.isEmpty()) break;
            paths.addAll(found);
        }
        List<Job> jobs = new ArrayList<>();
        for (String externalPath : paths.stream().filter(path -> path.startsWith("/job/")).toList()) {
            if (jobs.size() >= PER_COMPANY_DETAILED) break;
            Job job = parseWorkdayPosting(company, get(base + externalPath));
            if (job != null) jobs.add(job);
        }
        return jobs;
    }

    /** The facet whose values are countries, narrowed to the countries Harbor covers; empty if the site has none. */
    static Map<String, List<String>> workdayCountryFacet(JsonNode listing) {
        Map<String, String> wanted = new LinkedHashMap<>();
        for (String code : COUNTRIES) wanted.put(Countries.name(code).toLowerCase(Locale.ROOT), code);
        String bestParameter = null;
        List<String> bestIds = List.of();
        for (JsonNode facet : listing.path("facets")) {
            List<String> ids = new ArrayList<>();
            for (JsonNode value : facet.path("values"))
                if (wanted.containsKey(value.path("descriptor").asString("").toLowerCase(Locale.ROOT))) ids.add(value.path("id").asString(""));
            if (ids.size() > bestIds.size()) { bestIds = ids; bestParameter = facet.path("facetParameter").asString(""); }
        }
        return bestParameter == null ? Map.of() : Map.of(bestParameter, bestIds);
    }

    Job parseWorkdayPosting(Company company, String body) {
        JsonNode info = json.readTree(body).path("jobPostingInfo");
        String country = info.path("jobRequisitionLocation").path(COUNTRY).path("alpha2Code").asString("");
        String location = info.path(LOCATION).asString("");
        if (country.length() != 2) country = Countries.fromLocation(info.path(COUNTRY).path("descriptor").asString(location));
        String start = info.path("startDate").asString("");
        List<Job> one = new ArrayList<>();
        add(one, company, new Posting("wd-" + company.id() + "-" + info.path("jobReqId").asString(info.path("id").asString("")),
                info.path(TITLE).asString(""), country, location, "Remote".equalsIgnoreCase(info.path("remoteType").asString("")),
                ArbeitnowJobSource.plainText(info.path("jobDescription").asString("")),
                List.of(info.path("timeType").asString("")), info.path("externalUrl").asString(""),
                start.length() == 10 ? LocalDate.parse(start).atStartOfDay().toInstant(ZoneOffset.UTC) : null));
        return one.isEmpty() ? null : one.getFirst();
    }


    private static void add(List<Job> jobs, Company company, Posting p) {
        if (p.id().endsWith("-") || p.title() == null || p.title().isBlank() || p.url() == null || !p.url().startsWith("https://")) return;
        if (p.country() == null || !COUNTRIES.contains(p.country().toUpperCase(Locale.ROOT))) return;
        List<String> cleanTags = new ArrayList<>(new LinkedHashSet<>(p.tags().stream().filter(t -> t != null && !t.isBlank()).map(String::strip).toList()));
        jobs.add(new Job(p.id(), SOURCE, p.title().strip(), company.name(), p.country().toUpperCase(Locale.ROOT), p.location(), p.remote(),
                VisaSignals.read(p.title() + "\n" + p.description(), null), p.description(), cleanTags, p.url(), p.postedAt(), company.id()));
    }

    static List<Job> newest(List<Job> jobs, int max) {
        Map<String, Job> unique = new LinkedHashMap<>();
        jobs.forEach(job -> unique.putIfAbsent(job.id(), job));
        return unique.values().stream()
                .sorted(Comparator.comparing(Job::postedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(max).toList();
    }

    private String get(String url) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(URI.create(url)).GET());
    }

    private String post(String url, String body) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    private String send(HttpRequest.Builder request) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(request.timeout(Duration.ofSeconds(25)).header("Accept", "application/json")
                .header("User-Agent", "DeepwaterHarbor/1.0").build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new BoardException("status " + response.statusCode());
        return response.body();
    }

    private static String path(String segment) {
        return URLEncoder.encode(segment == null ? "" : segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static Instant instant(String value) {
        if (value == null || value.isBlank()) return null;
        try { return OffsetDateTime.parse(value).toInstant(); }
        catch (java.time.format.DateTimeParseException _) {
            try { return Instant.parse(value); } catch (java.time.format.DateTimeParseException _) { return null; }
        }
    }

    /** Greenhouse sends its HTML entity-escaped ("&lt;p&gt;"); undo that before reading the tags. */
    static String unescape(String html) {
        return html.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
    }

    public record CompanyStatus(int jobs, Instant checkedAt, String problem) {}

    /** A job board answered with something other than 200 OK. */
    static final class BoardException extends IOException {
        BoardException(String message) { super(message); }
    }

    /** One posting as read from a board, before it becomes a {@link Job}. */
    record Posting(String id, String title, String country, String location, boolean remote, String description,
                   List<String> tags, String url, Instant postedAt) {}
}
