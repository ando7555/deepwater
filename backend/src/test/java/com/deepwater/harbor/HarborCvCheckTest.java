package com.deepwater.harbor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

/** The CV styling and formatting check: what it flags and how it scores. */
class HarborCvCheckTest {
    static final String GOOD_CV = """
            Ana Example
            Senior Backend Engineer
            ana@example.test | +45 12 34 56 78 | linkedin.com/in/ana-example

            SUMMARY
            Backend engineer with a focus on payment systems in Java and Spring Boot for Nordic banks and shops.
            Moving to Copenhagen in 2027 and looking for a senior role in a product team that values testing.

            EXPERIENCE
            Nordic Pay, Copenhagen — Backend Engineer, 2019 – present
            • Built the card payment service in Java 21 and Spring Boot, handling 3 million transactions a month.
            • Cut checkout response time by 40% by moving fraud checks to Kafka events.
            • Led a team of 4 engineers through the move from a monolith to 12 services.
            • Introduced contract tests with JUnit and Testcontainers; production incidents fell by 30%.
            Shop Co, Yerevan — Java Developer, 2016 – 2019
            • Wrote REST APIs for an online shop with 200k customers using Spring and PostgreSQL.
            • Automated deployments with Docker and GitLab CI, from monthly to daily releases.
            • Mentored 2 junior developers and ran the weekly code review.
            • Replaced nightly batch exports with a streaming pipeline, so reports were ready 6 hours earlier.

            EDUCATION
            BSc Computer Science, Yerevan State University, 2012 – 2016

            SKILLS
            Java, Spring Boot, Kafka, PostgreSQL, Docker, Kubernetes, GraphQL, JUnit, Git, Agile

            LANGUAGES
            Armenian (native), English (fluent), Danish (A2)

            PROJECTS
            • Open-source contributor to a Spring Boot starter for idempotent REST endpoints, used by 50 projects.
            • Wrote a series of 6 articles on event-driven design with Kafka for a Nordic developer community.
            • Built a personal finance tracker with React and Spring Boot to practise frontend work.
            """;

    static final String POOR_CV = """
            Ana Example
            I am a developer and I like my job. I worked at a bank and at a shop and I did many things there with my team,
            for example I wrote code and I fixed bugs and I talked to my colleagues about my ideas and I learned a lot.
            """;

    private static Map<String, String> statuses(CvCheck check) {
        return check.items().stream().collect(Collectors.toMap(CvCheck.Item::id, CvCheck.Item::status));
    }

    @Test
    void aWellStructuredCvPassesTheTextChecks() {
        var check = CvChecker.check(GOOD_CV, null);
        var status = statuses(check);
        assertThat(status).containsEntry("email", "PASS").containsEntry("phone", "PASS").containsEntry("links", "PASS")
                .containsEntry("sections", "PASS").containsEntry("headline", "PASS").containsEntry("dates", "PASS")
                .containsEntry("bullets", "PASS").containsEntry("paragraphs", "PASS").containsEntry("results", "PASS")
                .containsEntry("voice", "PASS").containsEntry("words", "PASS")
                .doesNotContainKeys("pages", "fonts", "fontsize", "readable");
        assertThat(check.score()).isEqualTo(100);
    }

    @Test
    void aWeakCvGetsConcreteFixes() {
        var check = CvChecker.check(POOR_CV, null);
        var status = statuses(check);
        assertThat(status).containsEntry("email", "FAIL").containsEntry("words", "FAIL").containsEntry("sections", "FAIL")
                .containsEntry("phone", "WARN").containsEntry("bullets", "WARN").containsEntry("results", "WARN")
                .containsEntry("voice", "WARN").containsEntry("dates", "WARN").containsEntry("headline", "WARN");
        assertThat(check.items()).filteredOn(i -> i.id().equals("sections")).first()
                .extracting(CvCheck.Item::detail).asString().contains("Experience, Education, Skills");
        assertThat(check.score()).isLessThan(50);
    }

    @Test
    void layoutChecksFlagTooManyPagesFontsAndTinyText() {
        var layout = new CvExtractor.CvLayout(4, List.of("Arial", "Calibri", "Georgia", "Verdana"), 8f);
        var status = statuses(CvChecker.check(GOOD_CV, layout));
        assertThat(status).containsEntry("readable", "PASS").containsEntry("pages", "FAIL").containsEntry("fonts", "FAIL")
                .containsEntry("fontsize", "WARN");
        var calm = statuses(CvChecker.check(GOOD_CV, new CvExtractor.CvLayout(2, List.of("Calibri"), 10.5f)));
        assertThat(calm).containsEntry("pages", "PASS").containsEntry("fonts", "PASS").containsEntry("fontsize", "PASS");
    }

    @Test
    void scoreCountsWarningsAsHalf() {
        assertThat(CvChecker.score(List.of(new CvCheck.Item("a", "PASS", "", ""), new CvCheck.Item("b", "WARN", "", ""),
                new CvCheck.Item("c", "FAIL", "", ""), new CvCheck.Item("d", "PASS", "", "")))).isEqualTo(63);
    }

    @Test
    void pdfLayoutIsReadFromTheFile() throws Exception {
        var document = CvExtractor.read("cv.pdf", pdf());
        assertThat(document.layout().pages()).isEqualTo(2);
        assertThat(document.layout().fontFamilies()).containsExactly("Courier", "Helvetica", "Times");
        assertThat(document.layout().smallestBodySize()).isEqualTo(8f);
        assertThat(CvExtractor.read("cv.txt", "plain text cv".getBytes()).layout()).isNull();
    }

    @Test
    void fontNamesBecomeFamilies() {
        assertThat(CvExtractor.family("ABCDEF+Calibri-Bold")).isEqualTo("Calibri");
        assertThat(CvExtractor.family("Arial,Italic")).isEqualTo("Arial");
        assertThat(CvExtractor.family("ArialMT")).isEqualTo("Arial");
        assertThat(CvExtractor.family("Helvetica-BoldOblique")).isEqualTo("Helvetica");
    }

    @Test
    void wordSymbolBulletsCountAsBullets() {
        String cv = GOOD_CV.replace("•", "\uF0B7");
        assertThat(statuses(CvChecker.check(cv, null))).containsEntry("bullets", "PASS").containsEntry("paragraphs", "PASS");
    }

    /** Two pages: Helvetica and Times headings, then 300 characters of 8 pt Courier body text. */
    static byte[] pdf() throws Exception {
        try (var document = new PDDocument(); var out = new ByteArrayOutputStream()) {
            String body = "Built services in Java. ".repeat(13);
            Object[][] runs = {{Standard14Fonts.FontName.HELVETICA_BOLD, 16f, "Ana Example"},
                    {Standard14Fonts.FontName.TIMES_ROMAN, 12f, "Senior Backend Engineer"}, {Standard14Fonts.FontName.COURIER, 8f, body}};
            for (int pageNumber = 0; pageNumber < 2; pageNumber++) {
                var page = new PDPage();
                document.addPage(page);
                try (var content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.newLineAtOffset(40, 740);
                    for (Object[] run : runs) {
                        content.setFont(new PDType1Font((Standard14Fonts.FontName) run[0]), (Float) run[1]);
                        content.showText((String) run[2]);
                        content.newLineAtOffset(0, -20);
                    }
                    content.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
