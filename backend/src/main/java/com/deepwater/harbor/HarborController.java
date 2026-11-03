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

    public HarborController(PlatformService platform, TalentProfileService profiles, JobSearchService search,
                            ApplicationService applications, ArbeitnowJobSource arbeitnow, JobCatalogService catalog) {
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
                                       @RequestParam(required = false) Integer max) {
        return search.search(platform.requireAccount(auth), new JobSearchService.Filters(q, country, remote, visa, minScore, source, max));
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
        result.put("countries", Countries.NAMES);
        result.put("permitGuides", List.copyOf(WorkPermitRules.guides().values()));
        return result;
    }

    @PostMapping("/jobs/refresh")
    ArbeitnowJobSource.Refresh refresh(@RequestHeader(value = "Authorization", required = false) String auth) {
        platform.requireAccount(auth);
        return arbeitnow.refresh();
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
    record TrackRequest(String status, String note) {}
}
