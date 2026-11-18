package com.deepwater.harbor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Real employer postings: the five job-board formats, country filtering and the hiring-process reader. */
class HarborCompanyJobsTest {
    private final CompanyRegistry registry = new CompanyRegistry(new ObjectMapper());
    private final CompanyJobSource source = new CompanyJobSource(false, registry, null);

    @Test
    void registryHasTheOwnersDanishCompaniesAndEveryBoardIsComplete() {
        assertThat(registry.all()).extracting(CompanyRegistry.Company::name).contains("JYSK", "Arla Foods", "LEGO Group", "Danske Bank",
                "Netcompany", "Deloitte", "Uber", "QuantumBlack, AI by McKinsey", "Accenture", "Databricks");
        assertThat(registry.all()).allSatisfy(company -> {
            assertThat(company.domain()).matches("[a-z0-9.-]+\\.[a-z]+");
            assertThat(company.careersUrl()).startsWith("https://");
            if (company.connected()) assertThat(company.board().id()).isNotBlank();
            if ("WORKDAY".equals(company.board().type())) assertThat(company.board().host()).endsWith(".myworkdayjobs.com");
        });
        assertThat(registry.all().stream().map(CompanyRegistry.Company::home).distinct()).contains("DK", "SE", "NO", "DE", "CH");
    }

    @Test
    void greenhouseKeepsCoveredCountriesAndReadsEscapedHtml() {
        String body = """
            {"jobs":[
              {"id":101,"title":"Backend Engineer","absolute_url":"https://boards.greenhouse.io/x/jobs/101","location":{"name":"Copenhagen"},
               "first_published":"2026-11-10T09:00:00-05:00","departments":[{"name":"Engineering"}],
               "content":"&lt;p&gt;Java and Kafka.&lt;/p&gt;&lt;h3&gt;Interview Process&lt;/h3&gt;&lt;p&gt;Recruiter Screen → Hiring Manager Screen → Live Coding → Reference Check&lt;/p&gt;"},
              {"id":102,"title":"Account Executive","absolute_url":"https://boards.greenhouse.io/x/jobs/102","location":{"name":"New York"},"content":""},
              {"id":103,"title":"Data Engineer","absolute_url":"https://boards.greenhouse.io/x/jobs/103","location":{"name":"Remote"},
               "offices":[{"location":"Zürich, Switzerland"}],"content":"&lt;p&gt;Spark&lt;/p&gt;"}]}""";
        List<Job> jobs = source.parseGreenhouse(registry.find("databricks"), body);
        assertThat(jobs).extracting(Job::id).containsExactly("gh-101", "gh-103");
        Job first = jobs.getFirst();
        assertThat(first.countryCode()).isEqualTo("DK");
        assertThat(first.companyId()).isEqualTo("databricks");
        assertThat(first.source()).isEqualTo("COMPANY");
        assertThat(first.description()).contains("Java and Kafka.").doesNotContain("<p>", "&lt;");
        assertThat(first.tags()).containsExactly("Engineering");
        assertThat(jobs.get(1).countryCode()).isEqualTo("CH");
        assertThat(jobs.get(1).remote()).isTrue();

        HiringProcess process = HiringProcess.read(first.description());
        assertThat(process.steps()).extracting(HiringProcess.Step::name)
                .containsExactly("Recruiter Screen", "Hiring Manager Screen", "Live Coding", "Reference Check");
        assertThat(process.interviews()).isEqualTo(2);
    }

    @Test
    void leverAshbySmartRecruitersAndWorkdayMapToJobs() {
        List<Job> lever = source.parseLever(registry.find("spotify"), """
            [{"id":"a1","text":"Data Scientist","country":"SE","categories":{"location":"Stockholm","team":"Insights","commitment":"Permanent"},
              "descriptionPlain":"Python.","lists":[{"text":"What you'll do","content":"<li>Model</li>"}],"additionalPlain":"",
              "hostedUrl":"https://jobs.lever.co/spotify/a1","createdAt":1792088307254,"workplaceType":"hybrid"},
             {"id":"a2","text":"Engineer","country":"US","categories":{"location":"New York"},"hostedUrl":"https://jobs.lever.co/spotify/a2"}]""");
        assertThat(lever).extracting(Job::id).containsExactly("lever-a1");
        assertThat(lever.getFirst().description()).contains("Python.", "What you'll do", "Model");

        List<Job> ashby = source.parseAshby(registry.find("pleo"), """
            {"jobs":[{"id":"b1","title":"Product Manager","location":"Copenhagen","isListed":true,"isRemote":false,"workplaceType":"Hybrid",
              "address":{"postalAddress":{"addressCountry":"Denmark"}},"jobUrl":"https://jobs.ashbyhq.com/pleo/b1","publishedAt":"2026-11-03T10:26:33.784+00:00",
              "department":"Product","descriptionPlain":"The interview process\\nIntro call: A 30-minute chat with our Talent Partner.\\nHiring manager interview: A conversation with the Head of Product.\\nCase study: A take-home task.\\nFinal interview: With a senior leader.\\nShow me the benefits!"},
             {"id":"b2","title":"Hidden","location":"Copenhagen","isListed":false,"jobUrl":"https://jobs.ashbyhq.com/pleo/b2"}]}""");
        assertThat(ashby).hasSize(1);
        HiringProcess process = HiringProcess.read(ashby.getFirst().description());
        assertThat(process.steps()).extracting(HiringProcess.Step::name)
                .containsExactly("Intro call", "Hiring manager interview", "Case study", "Final interview");
        assertThat(process.interviews()).isEqualTo(3);
        assertThat(process.steps().getFirst().detail()).isEqualTo("A 30-minute chat with our Talent Partner.");

        Job jysk = source.parseSmartRecruitersPosting(registry.find("jysk"), """
            {"id":"744","name":"Butiksassistent","location":{"city":"Aarhus","country":"dk","remote":false},
             "postingUrl":"https://jobs.smartrecruiters.com/JYSK/744-butiksassistent","releasedDate":"2026-11-18T11:28:05.321Z",
             "function":{"label":"Retail"},"typeOfEmployment":{"label":"Full-time"},
             "jobAd":{"sections":{"jobDescription":{"title":"Stillingsbeskrivelse","text":"<p>Du hjælper kunder.</p>"}}}}""");
        assertThat(jysk.countryCode()).isEqualTo("DK");
        assertThat(jysk.location()).isEqualTo("Aarhus, Denmark");
        assertThat(jysk.description()).contains("Stillingsbeskrivelse", "Du hjælper kunder.");

        Job lego = source.parseWorkdayPosting(registry.find("lego"), """
            {"jobPostingInfo":{"id":"x","title":"Planner","jobDescription":"<p>Plan things.</p>","location":"Billund","startDate":"2026-11-17",
             "timeType":"Full time","jobReqId":"0000040073","externalUrl":"https://lego.wd103.myworkdayjobs.com/LEGO_External/job/Billund/Planner_0000040073",
             "jobRequisitionLocation":{"country":{"alpha2Code":"DK"}}}}""");
        assertThat(lego.id()).isEqualTo("wd-lego-0000040073");
        assertThat(lego.countryCode()).isEqualTo("DK");
        assertThat(lego.description()).isEqualTo("Plan things.");

        var listing = new ObjectMapper().readTree("""
            {"facets":[{"facetParameter":"jobFamily","values":[{"descriptor":"IT","id":"f1"}]},
                       {"facetParameter":"Location","values":[{"descriptor":"United States of America","id":"us"},{"descriptor":"Denmark","id":"dk"},{"descriptor":"Germany","id":"de"}]}]}""");
        assertThat(CompanyJobSource.workdayCountryFacet(listing)).isEqualTo(Map.of("Location", List.of("dk", "de")));
    }

    @Test
    void hiringProcessIsOnlyWhatThePostingSays() {
        assertThat(HiringProcess.read("If you need adjustments during the recruitment process, please let us know.")).isNull();
        assertThat(HiringProcess.read("Interview process\nWe will get back to you soon.")).isNull();
        assertThat(HiringProcess.read(null)).isNull();

        HiringProcess numbered = HiringProcess.read("Our recruitment process\nStep 1 – Screening call with Talent Acquisition\nStep 2 – Interview with our Country Director\nStep 3 – Case presentation\nAbout us");
        assertThat(numbered.steps()).extracting(HiringProcess.Step::name)
                .containsExactly("Screening call with Talent Acquisition", "Interview with our Country Director", "Case presentation");
        assertThat(numbered.interviews()).isEqualTo(2);

        HiringProcess prose = HiringProcess.read("Next steps\nThe recruitment process will include an initial conversation with Talent Acquisition, an interview with the hiring team and a practical assessment relevant to the role. We review applications on a rolling basis.");
        assertThat(prose.steps()).extracting(HiringProcess.Step::name)
                .containsExactly("Initial conversation with Talent Acquisition", "Interview with the hiring team", "Practical assessment relevant to the role");
        assertThat(prose.interviews()).isEqualTo(2);

        HiringProcess plainList = HiringProcess.read("Our hiring process typically includes:\nAn introductory chat with our TA team\nA conversation with the Hiring Manager\nA practical assignment\nFinal interviews with the wider team\nWe review applications on a rolling basis, so apply today!");
        assertThat(plainList.steps()).hasSize(4);
        assertThat(plainList.interviews()).isEqualTo(3);
    }

    @Test
    void newestKeepsOnePostingPerIdAndTheMostRecentFirst() {
        Job old = new Job("a", "COMPANY", "Old", "X", "DK", "", false, "UNKNOWN", "", List.of(), "https://x", java.time.Instant.parse("2026-01-01T00:00:00Z"), "x");
        Job fresh = new Job("b", "COMPANY", "New", "X", "DK", "", false, "UNKNOWN", "", List.of(), "https://x", java.time.Instant.parse("2026-11-01T00:00:00Z"), "x");
        Job undated = new Job("c", "COMPANY", "Undated", "X", "DK", "", false, "UNKNOWN", "", List.of(), "https://x", null, "x");
        assertThat(CompanyJobSource.newest(List.of(old, undated, fresh, fresh), 2)).extracting(Job::id).containsExactly("b", "a");
    }
}
