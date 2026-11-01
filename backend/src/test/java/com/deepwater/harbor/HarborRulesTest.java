package com.deepwater.harbor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Year;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The fixed rules behind Harbor: what is read from a CV, what a posting says about visas, and how a match is scored. */
class HarborRulesTest {
    static final String CV = """
            Ana Example
            ana@example.test | +45 12 34 56 78 | linkedin.com/in/ana
            Senior Backend Engineer

            SUMMARY
            Backend engineer with 8 years of experience building payment systems in Java and Spring Boot.
            I like event-driven design with Kafka and clean REST APIs.

            EXPERIENCE
            Nordic Pay, Copenhagen — Backend Engineer, 2019 – present
            Built services with Java 21, Spring Boot, PostgreSQL, Docker and Kubernetes on Azure.
            Shop Co, Yerevan — Java Developer, 2016 – 2019

            SKILLS
            Java, Spring, Kafka, PostgreSQL, Docker, Kubernetes, GraphQL, JUnit, Git, Agile

            LANGUAGES
            Armenian (native), English (fluent), Danish (A2)
            """;

    @Test
    void cvBecomesADraftProfile() {
        var draft = CvExtractor.extract(CV);
        assertThat(draft.headline()).isEqualTo("Senior Backend Engineer");
        assertThat(draft.summary()).startsWith("Backend engineer with 8 years").contains("Kafka and clean REST APIs.");
        assertThat(draft.yearsExperience()).isEqualTo(8);
        assertThat(draft.skills()).contains("Java", "Spring", "Kafka", "PostgreSQL", "Docker", "Kubernetes", "GraphQL", "JUnit", "Git",
                "Agile", "Azure", "REST").doesNotContain("JavaScript", "Go");
        assertThat(draft.languages()).containsExactly("English", "Danish", "Armenian");
    }

    @Test
    void yearsComeFromDateRangesWhenTheCvDoesNotStateThem() {
        assertThat(CvExtractor.years("Developer, 2015 - 2018\nLead, 2018 – present")).isEqualTo(Year.now().getValue() - 2015);
        assertThat(CvExtractor.years("No dates here")).isNull();
    }

    @Test
    void skillsRespectWordEdges() {
        assertThat(SkillCatalog.find("JavaScript and TypeScript")).containsExactly("JavaScript", "TypeScript");
        assertThat(SkillCatalog.find("C# and .NET, some C++")).containsExactly("C#", ".NET", "C++");
        assertThat(SkillCatalog.find("I excel at the rest of the work")).isEmpty();
        assertThat(SkillCatalog.mentions("We use Golang", "go")).isTrue();
        assertThat(SkillCatalog.mentions("Experience with Camunda", "Camunda")).isTrue();
        assertThat(SkillCatalog.normalise(List.of(" spring boot", "Spring", "k8s", ""))).containsExactly("Spring", "Kubernetes");
    }

    @Test
    void postingsSayWhetherTheySponsorVisas() {
        assertThat(VisaSignals.read("We offer relocation and visa sponsorship.", null)).isEqualTo("OFFERED");
        assertThat(VisaSignals.read("No visa sponsorship is available.", null)).isEqualTo("NOT_OFFERED");
        assertThat(VisaSignals.read("You must have the right to work in Ireland.", null)).isEqualTo("NOT_OFFERED");
        assertThat(VisaSignals.read("Great team, nice office.", null)).isEqualTo("UNKNOWN");
        assertThat(VisaSignals.read("Great team.", true)).isEqualTo("OFFERED");
    }

    @Test
    void workPermitNeedFollowsTheFreeMovementArea() {
        var other = WorkPermitRules.Citizenship.OTHER;
        var eu = WorkPermitRules.Citizenship.EU_EEA;
        assertThat(WorkPermitRules.need(other, "DK")).isEqualTo(WorkPermitRules.PermitNeed.NEEDED);
        assertThat(WorkPermitRules.need(other, "CH")).isEqualTo(WorkPermitRules.PermitNeed.NEEDED);
        assertThat(WorkPermitRules.need(eu, "NO")).isEqualTo(WorkPermitRules.PermitNeed.NOT_NEEDED);
        assertThat(WorkPermitRules.need(WorkPermitRules.Citizenship.SWISS, "SE")).isEqualTo(WorkPermitRules.PermitNeed.NOT_NEEDED);
        assertThat(WorkPermitRules.need(eu, "US")).isEqualTo(WorkPermitRules.PermitNeed.CHECK);
        assertThat(WorkPermitRules.need(WorkPermitRules.Citizenship.UNKNOWN, "DK")).isEqualTo(WorkPermitRules.PermitNeed.CHECK);
        assertThat(WorkPermitRules.GUIDES.keySet()).containsExactly("DK", "SE", "NO", "CH");
    }

    @Test
    void matchScoreExplainsItself() {
        var profile = new TalentProfile("Senior Backend Engineer", null, List.of("Java", "Spring", "Kafka", "PostgreSQL", "Docker"), 8,
                List.of(), "OTHER", List.of("DK", "CH"), "ANY", null, false, null);
        var job = new Job("j1", "SAMPLE", "Backend Engineer (Java)", "Co", "DK", "Copenhagen", false, "OFFERED",
                "Java and Spring Boot with Kafka. Kotlin is a plus.", List.of("Java", "Kotlin"), null, null);
        var match = JobMatcher.match(profile, job);
        assertThat(match.matchedSkills()).containsExactly("Java", "Spring", "Kafka");
        assertThat(match.missingSkills()).containsExactly("Kotlin");
        // 3 of 5 skills = 36, title shares "backend engineer" = 19, target country = 15
        assertThat(match.score()).isEqualTo(70);
        assertThat(match.workPermit()).isEqualTo("NEEDED");
        assertThat(match.reasons()).contains("Mentions 3 of your skills: Java, Spring, Kafka.",
                "Title shares \"backend engineer\" with your headline.", "In Denmark, one of your target countries.");

        var unrelated = new Job("j2", "SAMPLE", "Nurse", "Clinic", "US", "Boston", false, "UNKNOWN", "Care for patients.", List.of(), null, null);
        assertThat(JobMatcher.match(profile, unrelated).score()).isZero();
    }

    @Test
    void arbeitnowPagesBecomePostings() {
        var source = new ArbeitnowJobSource(true, "http://unused", null);
        var jobs = source.parse("""
                {"data":[
                  {"slug":"java-dev-berlin-1","company_name":"Acme GmbH","title":"Java Developer (m/w/d)",
                   "description":"<p>Java &amp; Spring.</p><p>We offer visa sponsorship.</p>","remote":false,
                   "url":"https://www.arbeitnow.com/jobs/acme/java-dev-berlin-1","tags":["Java","Spring"],"job_types":["full time"],
                   "location":"Berlin","created_at":1761955200},
                  {"slug":"bad","title":"No link","url":"javascript:alert(1)"}
                ],"links":{},"meta":{}}
                """);
        assertThat(jobs).hasSize(1);
        var job = jobs.getFirst();
        assertThat(job.id()).isEqualTo("arbeitnow-java-dev-berlin-1");
        assertThat(job.countryCode()).isEqualTo("DE");
        assertThat(job.visaSignal()).isEqualTo("OFFERED");
        assertThat(job.description()).isEqualTo("Java & Spring.\nWe offer visa sponsorship.");
        assertThat(job.tags()).containsExactly("Java", "Spring");
        assertThat(job.postedAt()).isNotNull();
    }

    @Test
    void locationsArePlacedInCountries() {
        assertThat(Countries.fromLocation("Zürich, Switzerland")).isEqualTo("CH");
        assertThat(Countries.fromLocation("Remote (Europe)")).isNull();
        assertThat(Countries.fromLocation("København K")).isEqualTo("DK");
    }
}
