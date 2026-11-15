package com.deepwater.harbor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Job families and the local-language need are read with fixed rules from the posting. */
class JobCategoriesTest {
    private static Job job(String title, String... tags) {
        return new Job("x", "SAMPLE", title, "Co (sample)", "DK", "Copenhagen", false, "UNKNOWN", "", List.of(tags), null, null);
    }

    @Test
    void titlesDecideTheJobFamily() {
        assertThat(JobCategories.category(job("Senior Backend Engineer (Java, Spring Boot)"))).isEqualTo("BACKEND");
        assertThat(JobCategories.category(job("Full-stack Developer (React, Java)"))).isEqualTo("FRONTEND");
        assertThat(JobCategories.category(job("Platform Engineer (Kubernetes, Terraform)"))).isEqualTo("CLOUD_DEVOPS");
        assertThat(JobCategories.category(job("Site Reliability Engineer"))).isEqualTo("CLOUD_DEVOPS");
        assertThat(JobCategories.category(job("Machine Learning Engineer"))).isEqualTo("DATA_AI");
        assertThat(JobCategories.category(job("AI Engineer (LLM applications)"))).isEqualTo("DATA_AI");
        assertThat(JobCategories.category(job("Product Owner, Mobile Banking"))).isEqualTo("PRODUCT_LEAD");
        assertThat(JobCategories.category(job("Engineering Manager", "Java"))).isEqualTo("PRODUCT_LEAD");
    }

    @Test
    void tagsDecideWhenTheTitleIsVague() {
        assertThat(JobCategories.category(job("Consultant", "Python", "Spark"))).isEqualTo("BACKEND");
        assertThat(JobCategories.category(job("Consultant", "Kubernetes"))).isEqualTo("CLOUD_DEVOPS");
        assertThat(JobCategories.category(job("Shop assistant"))).isEqualTo(JobCategories.OTHER);
        assertThat(JobCategories.orderedLabels()).containsEntry("OTHER", "Other roles").hasSize(6);
    }

    @Test
    void postingTextSaysHowMuchTheLocalLanguageMatters() {
        assertThat(JobCategories.languageNeed("English is our working language; Danish is a plus but not required."))
                .isEqualTo(new JobCategories.LanguageNeed("PLUS", "Danish"));
        assertThat(JobCategories.languageNeed("Danish at a professional level is required for stakeholder work."))
                .isEqualTo(new JobCategories.LanguageNeed("REQUIRED", "Danish"));
        assertThat(JobCategories.languageNeed("German at B2 level or better."))
                .isEqualTo(new JobCategories.LanguageNeed("REQUIRED", "German"));
        assertThat(JobCategories.languageNeed("Norwegian is not required."))
                .isEqualTo(new JobCategories.LanguageNeed("ENGLISH_OK", null));
        assertThat(JobCategories.languageNeed("English-speaking team."))
                .isEqualTo(new JobCategories.LanguageNeed("ENGLISH_OK", null));
        // Naming a language without saying anything about it is not a requirement.
        assertThat(JobCategories.languageNeed("Build learning tools used by Danish schools."))
                .isEqualTo(new JobCategories.LanguageNeed("NOT_STATED", null));
        assertThat(JobCategories.languageNeed(null)).isEqualTo(new JobCategories.LanguageNeed("NOT_STATED", null));
    }
}
