package com.deepwater.harbor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * The employers Harbor follows, from harbor/companies.json. Each one names its own public job board
 * (Greenhouse, Lever, Ashby, SmartRecruiters or Workday), or NONE when Harbor has no public feed for it
 * yet: those companies are listed with a link to their careers page and no postings.
 */
@Component
public class CompanyRegistry {
    private final Map<String, Company> companies = new LinkedHashMap<>();

    public CompanyRegistry(ObjectMapper json) {
        try {
            Company[] all = json.readValue(new ClassPathResource("harbor/companies.json").getInputStream().readAllBytes(), Company[].class);
            for (Company company : all) companies.put(company.id(), company);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public List<Company> all() { return List.copyOf(companies.values()); }

    public Company find(String id) { return id == null ? null : companies.get(id); }

    public record Company(String id, String name, String domain, String home, String careersUrl, Board board) {
        public boolean connected() { return board != null && !"NONE".equals(board.type()); }
    }

    /** type: GREENHOUSE, LEVER, ASHBY, SMARTRECRUITERS, WORKDAY or NONE. host and site are for Workday only. */
    public record Board(String type, String id, String host, String site) {}
}
