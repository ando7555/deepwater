package com.deepwater.harbor;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Scores every recent posting against the person's profile, then applies the filters they chose. */
@Service
public class JobSearchService {
    private static final String NOT_NEEDED = "NOT_NEEDED";
    private static final String OFFERED = "OFFERED";
    private static final String SAMPLE = "SAMPLE";
    private final JobCatalogService catalog;
    private final TalentProfileService profiles;
    private final ApplicationService applications;

    public JobSearchService(JobCatalogService catalog, TalentProfileService profiles, ApplicationService applications) {
        this.catalog = catalog;
        this.profiles = profiles;
        this.applications = applications;
    }

    public SearchResult search(String accountId, Filters filters) {
        TalentProfile profile = profiles.profile(accountId);
        Map<String, String> statuses = applications.statusesByJob(accountId);
        List<JobMatch> all = catalog.recent().stream()
                .map(job -> new JobMatch(view(job), JobMatcher.match(profile, job), statuses.get(job.id())))
                .toList();
        List<JobMatch> shown = all.stream().filter(match -> filters.accepts(match))
                .sorted(Comparator.comparingInt((JobMatch m) -> m.match().score()).reversed().thenComparing(m -> m.job().title()))
                .limit(filters.limit())
                .toList();
        Map<String, Long> countries = all.stream().filter(m -> m.job().countryCode() != null)
                .collect(Collectors.groupingBy(m -> m.job().countryCode(), java.util.TreeMap::new, Collectors.counting()));
        Map<String, Long> categories = all.stream()
                .collect(Collectors.groupingBy(m -> m.job().category(), java.util.TreeMap::new, Collectors.counting()));
        return new SearchResult(shown, all.size(), countries, categories, JobCategories.orderedLabels(), profile.skills().isEmpty());
    }

    static JobView view(Job job) {
        JobCategories.LanguageNeed language = JobCategories.languageNeed(job.description());
        return new JobView(job.id(), job.source(), job.title(), job.company(), job.countryCode(), Countries.name(job.countryCode()),
                job.location(), job.remote(), job.visaSignal(), job.description(), job.tags(), job.url(), job.postedAt(),
                JobCategories.category(job), language.need(), language.language());
    }

    /**
     * visa: ANY; SPONSORED (the posting says it sponsors); NO_PERMIT_NEEDED (the person can work there without one);
     * OPEN_TO_ME (no permit needed, or the posting offers sponsorship); NOT_RULED_OUT (hides postings that refuse sponsorship).
     * remote: ANY, REMOTE or ONSITE. source: ALL, SAMPLE or LIVE. category: ANY or a code from {@link JobCategories}.
     * language: ANY, or NO_LOCAL_REQUIRED to hide postings that require the local language.
     */
    public record Filters(String q, String country, String remote, String visa, Integer minScore, String source, Integer max,
                          String category, String language) {
        public Filters(String q, String country, String remote, String visa, Integer minScore, String source, Integer max) {
            this(q, country, remote, visa, minScore, source, max, null, null);
        }

        boolean accepts(JobMatch m) {
            JobView job = m.job();
            return keywordMatches(job) && countryMatches(job) && remoteMatches(job) && visaMatches(job, m.match().workPermit())
                    && (minScore == null || m.match().score() >= minScore) && sourceMatches(job) && categoryMatches(job)
                    && languageMatches(job);
        }

        private boolean categoryMatches(JobView job) {
            return category == null || category.isBlank() || "ANY".equals(category) || category.equals(job.category());
        }

        private boolean languageMatches(JobView job) {
            return !"NO_LOCAL_REQUIRED".equals(language) || !JobCategories.NEED_REQUIRED.equals(job.languageNeed());
        }

        private boolean keywordMatches(JobView job) {
            if (q == null || q.isBlank()) return true;
            String text = (job.title() + " " + job.company() + " " + String.join(" ", job.tags()) + " " + job.description()).toLowerCase(Locale.ROOT);
            return java.util.Arrays.stream(q.toLowerCase(Locale.ROOT).split("\\s+")).filter(w -> !w.isBlank()).allMatch(text::contains);
        }

        private boolean countryMatches(JobView job) {
            if (country == null || country.isBlank() || "ANY".equals(country)) return true;
            return job.countryCode() != null && List.of(country.toUpperCase(Locale.ROOT).split(",")).contains(job.countryCode());
        }

        private boolean remoteMatches(JobView job) {
            if ("REMOTE".equals(remote)) return job.remote();
            if ("ONSITE".equals(remote)) return !job.remote();
            return true;
        }

        private boolean visaMatches(JobView job, String permit) {
            boolean noPermitNeeded = NOT_NEEDED.equals(permit);
            boolean sponsored = OFFERED.equals(job.visaSignal());
            return switch (visa == null ? "ANY" : visa) {
                case "SPONSORED" -> sponsored;
                case "NO_PERMIT_NEEDED" -> noPermitNeeded;
                case "OPEN_TO_ME" -> noPermitNeeded || sponsored;
                case "NOT_RULED_OUT" -> noPermitNeeded || !"NOT_OFFERED".equals(job.visaSignal());
                default -> true;
            };
        }

        private boolean sourceMatches(JobView job) {
            boolean sample = SAMPLE.equals(job.source());
            if (SAMPLE.equals(source)) return sample;
            if ("LIVE".equals(source)) return !sample;
            return true;
        }

        int limit() { return max == null ? 100 : Math.clamp(max, 1, 200); }
    }

    public record JobView(String id, String source, String title, String company, String countryCode, String countryName,
                          String location, boolean remote, String visaSignal, String description, List<String> tags, String url,
                          Instant postedAt, String category, String languageNeed, String localLanguage) {}

    public record JobMatch(JobView job, JobMatcher.Match match, String applicationStatus) {}

    public record SearchResult(List<JobMatch> jobs, int totalJobs, Map<String, Long> jobsByCountry, Map<String, Long> jobsByCategory,
                               Map<String, String> categoryLabels, boolean profileIsEmpty) {}
}
