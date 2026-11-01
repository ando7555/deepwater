package com.deepwater.harbor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Stores postings from every source in one table and loads the checked-in sample postings at start-up. */
@Service
public class JobCatalogService implements ApplicationRunner {
    /** Searches look at the newest postings only; enough for the MVP, and it keeps a search fast without an index. */
    static final int SEARCH_WINDOW = 1000;

    private final JdbcTemplate db;
    private final ObjectMapper json;

    public JobCatalogService(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        SampleJob[] samples = json.readValue(new ClassPathResource("harbor/sample-jobs.json").getInputStream().readAllBytes(), SampleJob[].class);
        for (SampleJob sample : samples) {
            String text = sample.title() + "\n" + sample.description();
            upsert(new Job(sample.id(), "SAMPLE", sample.title(), sample.company(), sample.countryCode(), sample.location(),
                    sample.remote(), VisaSignals.read(text, null), sample.description(), sample.tags(), null, null));
        }
    }

    @Transactional
    public void upsert(Job job) {
        Timestamp posted = job.postedAt() == null ? null : Timestamp.from(job.postedAt());
        String tags = String.join(",", (job.tags() == null ? List.<String>of() : job.tags()).stream()
                .map(tag -> tag.replace(",", " ").strip()).filter(tag -> !tag.isEmpty()).toList());
        int updated = db.update("update harbor_jobs set source=?,title=?,company=?,country_code=?,location=?,remote=?,visa_signal=?," +
                        "description=?,tags=?,url=?,posted_at=?,fetched_at=current_timestamp where id=?",
                job.source(), cut(job.title(), 300), cut(job.company(), 200), job.countryCode(), cut(job.location(), 200), job.remote(),
                job.visaSignal(), cut(job.description(), 20000), cut(tags, 1000), cut(job.url(), 1500), posted, job.id());
        if (updated == 0)
            db.update("insert into harbor_jobs(id,source,title,company,country_code,location,remote,visa_signal,description,tags,url,posted_at) " +
                            "values(?,?,?,?,?,?,?,?,?,?,?,?)",
                    job.id(), job.source(), cut(job.title(), 300), cut(job.company(), 200), job.countryCode(), cut(job.location(), 200),
                    job.remote(), job.visaSignal(), cut(job.description(), 20000), cut(tags, 1000), cut(job.url(), 1500), posted);
    }

    public List<Job> recent() {
        return db.query("select * from harbor_jobs order by coalesce(posted_at, fetched_at) desc, id limit " + SEARCH_WINDOW, JobCatalogService::row);
    }

    public Job find(String id) {
        return db.query("select * from harbor_jobs where id=?", JobCatalogService::row, id).stream().findFirst().orElse(null);
    }

    public int count(String source) {
        Integer count = db.queryForObject("select count(*) from harbor_jobs where source=?", Integer.class, source);
        return count == null ? 0 : count;
    }

    static Job row(ResultSet rs, int row) throws SQLException {
        String tags = rs.getString("tags");
        Timestamp posted = rs.getTimestamp("posted_at");
        return new Job(rs.getString("id"), rs.getString("source"), rs.getString("title"), rs.getString("company"),
                rs.getString("country_code"), rs.getString("location"), rs.getBoolean("remote"), rs.getString("visa_signal"),
                rs.getString("description"), tags == null || tags.isBlank() ? List.of() : Arrays.asList(tags.split(",")),
                rs.getString("url"), posted == null ? null : posted.toInstant());
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    record SampleJob(String id, String title, String company, String countryCode, String location, boolean remote,
                     List<String> tags, String description) {}
}
