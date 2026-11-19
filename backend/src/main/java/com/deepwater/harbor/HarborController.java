package com.deepwater.harbor;

import com.deepwater.platform.PlatformService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Harbor's REST API: talent profile from a CV, matched job search with visa filters, and the
 * person's application tracker. Every endpoint needs a Deepwater session (the same sign-in as Echo).
 */
@RestController
@RequestMapping("/api/v1/harbor")
public class HarborController {
    private final PlatformService platform;
    private final TalentProfileService profiles;
    private final JobSearchService search;
    private final ApplicationService applications;
    private final ArbeitnowJobSource arbeitnow;
    private final JobCatalogService catalog;
    private final CompanyRegistry companies;
    private final CompanyJobSource companyJobs;
    private final CompanyLogoService logos;

    public HarborController(PlatformService platform, TalentProfileService profiles, JobSearchService search,
                            ApplicationService applications, ArbeitnowJobSource arbeitnow, JobCatalogService catalog,
                            CompanyRegistry companies, CompanyJobSource companyJobs, CompanyLogoService logos) {
        this.companies = companies;
        this.companyJobs = companyJobs;
        this.logos = logos;
        this.platform = platform;
        this.profiles = profiles;
        this.search = search;
        this.applications = applications;
        this.arbeitnow = arbeitnow;
        this.catalog = catalog;
    }

    @GetMapping("/profile")
    TalentProfile profile(@RequestHeader(value = "Authorization", required = false) String auth) {
        return profiles.profile(platform.requireAccount(auth));
    }

    @PutMapping("/profile")
    TalentProfile updateProfile(@RequestHeader(value = "Authorization", required = false) String auth,
                                @RequestBody TalentProfileService.ProfileInput body) {
        return profiles.update(platform.requireAccount(auth), body);
    }

    /** Upload a PDF or text CV as multipart field "file". */
    @PostMapping("/cv")
    TalentProfile uploadCv(@RequestHeader(value = "Authorization", required = false) String auth,
                           @RequestPart("file") MultipartFile file) throws IOException {
        String accountId = platform.requireAccount(auth);
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is empty.");
        String name = file.getOriginalFilename() == null ? "cv" : file.getOriginalFilename();
        if (name.length() > 255) name = name.substring(name.length() - 255);
        return profiles.importCv(accountId, name, read(name, file.getBytes()));
    }

    /** For people who would rather paste their CV than upload a file. */
    @PostMapping("/cv/text")
    TalentProfile pasteCv(@RequestHeader(value = "Authorization", required = false) String auth, @RequestBody PastedCv body) {
        String accountId = platform.requireAccount(auth);
        if (body.text() == null || body.text().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paste the text of your CV.");
        return profiles.importCv(accountId, "Pasted text", read("pasted.txt", body.text().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @DeleteMapping("/cv")
    TalentProfile deleteCv(@RequestHeader(value = "Authorization", required = false) String auth) {
        return profiles.deleteCv(platform.requireAccount(auth));
    }

    @GetMapping("/jobs")
    JobSearchService.SearchResult jobs(@RequestHeader(value = "Authorization", required = false) String auth,
                                       @RequestParam(required = false) String q, @RequestParam(required = false) String country,
                                       @RequestParam(required = false) String remote, @RequestParam(required = false) String visa,
                                       @RequestParam(required = false) Integer minScore, @RequestParam(required = false) String source,
                                       @RequestParam(required = false) Integer max, @RequestParam(required = false) String category,
                                       @RequestParam(required = false) String language, @RequestParam(required = false) String company,
                                       @RequestParam(required = false, defaultValue = "0") int offset) {
        return search.search(platform.requireAccount(auth),
                new JobSearchService.Filters(q, country, remote, visa, minScore, source, max, category, language, company), offset);
    }

    /** Where the postings come from, and the work-permit guide for the Nordic countries and Switzerland. */
    @GetMapping("/sources")
    Map<String, Object> sources(@RequestHeader(value = "Authorization", required = false) String auth) {
        platform.requireAccount(auth);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("sampleJobs", catalog.count("SAMPLE"));
        result.put("liveJobs", catalog.count(ArbeitnowJobSource.SOURCE));
        result.put("liveEnabled", arbeitnow.enabled());
        result.put("lastRefreshedAt", arbeitnow.lastRefresh());
        result.put("companyJobs", catalog.count(CompanyJobSource.SOURCE));
        result.put("companyBoardsEnabled", companyJobs.enabled());
        result.put("companyBoardsRefreshing", companyJobs.running());
        result.put("companyBoardsRefreshedAt", companyJobs.lastRefresh());
        result.put("countries", Countries.NAMES);
        result.put("permitGuides", List.copyOf(WorkPermitRules.guides().values()));
        return result;
    }

    /** Refreshes Arbeitnow now (when it is on) and starts a background refresh of the company job boards. */
    @PostMapping("/jobs/refresh")
    ArbeitnowJobSource.Refresh refresh(@RequestHeader(value = "Authorization", required = false) String auth) {
        platform.requireAccount(auth);
        if (!companyJobs.enabled()) return arbeitnow.refresh();
        String companyNote = companyJobs.requestRefresh();
        if (!arbeitnow.enabled()) return new ArbeitnowJobSource.Refresh(companyJobs.enabled(), 0, companyNote, companyJobs.lastRefresh());
        ArbeitnowJobSource.Refresh live = arbeitnow.refresh();
        return new ArbeitnowJobSource.Refresh(true, live.updated(), live.message() + " " + companyNote, live.lastRefreshedAt());
    }

    /** The employers Harbor follows, with how many of their postings it holds and whether their board is connected. */
    @GetMapping("/companies")
    List<CompanyView> companies(@RequestHeader(value = "Authorization", required = false) String auth) {
        platform.requireAccount(auth);
        Map<String, Integer> counts = catalog.countsByCompany();
        return companies.all().stream().map(c -> {
            CompanyJobSource.CompanyStatus status = companyJobs.status(c.id());
            return new CompanyView(c.id(), c.name(), c.home(), Countries.name(c.home()), c.careersUrl(), c.connected(),
                    c.connected() ? c.board().type() : null, counts.getOrDefault(c.id(), 0), status == null ? null : status.problem());
        }).toList();
    }

    /**
     * A company's logo. Public, because an image tag cannot send the session header; it only ever serves
     * the logos of the companies listed in companies.json.
     */
    @GetMapping("/companies/{id}/logo")
    ResponseEntity<byte[]> logo(@PathVariable String id) {
        CompanyLogoService.Logo logo = logos.logo(id);
        if (logo == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType(logo.contentType()))
                .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePublic())
                .header("X-Content-Type-Options", "nosniff").body(logo.bytes());
    }

    @GetMapping("/applications")
    List<ApplicationService.Application> applications(@RequestHeader(value = "Authorization", required = false) String auth) {
        return applications.list(platform.requireAccount(auth));
    }

    @PutMapping("/applications/{jobId}")
    ApplicationService.Application track(@RequestHeader(value = "Authorization", required = false) String auth,
                                         @PathVariable String jobId, @RequestBody TrackRequest body) {
        return applications.track(platform.requireAccount(auth), jobId, body.status(), body.note());
    }

    @DeleteMapping("/applications/{jobId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void untrack(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String jobId) {
        applications.remove(platform.requireAccount(auth), jobId);
    }

    private static CvExtractor.CvDocument read(String name, byte[] bytes) {
        try { return CvExtractor.read(name, bytes); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage()); }
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> problem(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> tooLarge() {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "The CV must be 5 MB or smaller."));
    }

    record PastedCv(String text) {}
    record CompanyView(String id, String name, String home, String homeName, String careersUrl, boolean connected, String board,
                       int jobs, String problem) {}
    record TrackRequest(String status, String note) {}
}
