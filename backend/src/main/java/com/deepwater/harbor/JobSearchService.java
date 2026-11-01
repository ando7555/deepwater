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
        return new SearchResult(shown, all.size(), countries, profile.skills().isEmpty());
    }

    static JobView view(Job job) {
        return new JobView(job.id(), job.source(), job.title(), job.company(), job.countryCode(), Countries.name(job.countryCode()),
                job.location(), job.remote(), job.visaSignal(), job.description(), job.tags(), job.url(), job.postedAt());
    }

    /**
     * visa: ANY; SPONSORED (the posting says it sponsors); NO_PERMIT_NEEDED (the person can work there without one);
     * OPEN_TO_ME (no permit needed, or the posting offers sponsorship); NOT_RULED_OUT (hides postings that refuse sponsorship).
     * remote: ANY, REMOTE or ONSITE. source: ALL, SAMPLE or LIVE.
     */
    public record Filters(String q, String country, String remote, String visa, Integer minScore, String source, Integer max) {
        boolean accepts(JobMatch m) {
            JobView job = m.job();
            if (q != null && !q.isBlank()) {
                String text = (job.title() + " " + job.company() + " " + String.join(" ", job.tags()) + " " + job.description()).toLowerCase(Locale.ROOT);
                for (String word : q.toLowerCase(Locale.ROOT).split("\\s+")) if (!word.isBlank() && !text.contains(word)) return false;
            }
            if (country != null && !country.isBlank() && !"ANY".equals(country)) {
                List<String> codes = List.of(country.toUpperCase(Locale.ROOT).split(","));
                if (job.countryCode() == null || !codes.contains(job.countryCode())) return false;
            }
            if ("REMOTE".equals(remote) && !job.remote()) return false;
            if ("ONSITE".equals(remote) && job.remote()) return false;
            String permit = m.match().workPermit();
            switch (visa == null ? "ANY" : visa) {
                case "SPONSORED" -> { if (!"OFFERED".equals(job.visaSignal())) return false; }
                case "NO_PERMIT_NEEDED" -> { if (!"NOT_NEEDED".equals(permit)) return false; }
                case "OPEN_TO_ME" -> { if (!"NOT_NEEDED".equals(permit) && !"OFFERED".equals(job.visaSignal())) return false; }
                case "NOT_RULED_OUT" -> { if ("NOT_OFFERED".equals(job.visaSignal()) && !"NOT_NEEDED".equals(permit)) return false; }
                default -> { }
            }
            if (minScore != null && m.match().score() < minScore) return false;
            if ("SAMPLE".equals(source) && !"SAMPLE".equals(job.source())) return false;
            if ("LIVE".equals(source) && "SAMPLE".equals(job.source())) return false;
            return true;
        }

        int limit() { return max == null ? 100 : Math.max(1, Math.min(200, max)); }
    }

    public record JobView(String id, String source, String title, String company, String countryCode, String countryName,
                          String location, boolean remote, String visaSignal, String description, List<String> tags, String url,
                          Instant postedAt) {}

    public record JobMatch(JobView job, JobMatcher.Match match, String applicationStatus) {}

    public record SearchResult(List<JobMatch> jobs, int totalJobs, Map<String, Long> jobsByCountry, boolean profileIsEmpty) {}
}
