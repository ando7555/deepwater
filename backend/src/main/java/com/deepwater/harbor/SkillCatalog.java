package com.deepwater.harbor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The skills Harbor can recognise in a CV and in a job posting, each with the spellings people use.
 * It is a fixed list, not a model: a skill that is not listed here is only found if the person adds it
 * to their profile by hand. Matching is case-insensitive and respects word edges, so "Java" does not
 * match "JavaScript" and "Go" needs to appear as "Golang" or "Go" next to programming words.
 */
public final class SkillCatalog {
    private static final Map<String, List<String>> SKILLS = new LinkedHashMap<>();

    static {
        add("Java", "java");
        add("Spring", "spring boot", "spring framework", "spring", "springboot");
        add("Kotlin", "kotlin");
        add("Scala", "scala");
        add("Python", "python");
        add("Django", "django");
        add("FastAPI", "fastapi");
        add("JavaScript", "javascript", "ecmascript");
        add("TypeScript", "typescript");
        add("React", "react", "react.js", "reactjs");
        add("Angular", "angular");
        add("Vue", "vue", "vue.js", "vuejs");
        add("Node.js", "node.js", "nodejs");
        add("C#", "c#", "csharp");
        add(".NET", ".net", "dotnet", "asp.net");
        add("C++", "c++", "cpp");
        add("Go", "golang");
        add("Rust", "rust");
        add("PHP", "php");
        add("Ruby", "ruby", "ruby on rails", "rails");
        add("Swift", "swiftui", "swift programming", "swift/ios");
        add("Android", "android");
        add("iOS", "ios");
        add("SQL", "sql");
        add("PostgreSQL", "postgresql", "postgres");
        add("MySQL", "mysql");
        add("Oracle", "oracle");
        add("MongoDB", "mongodb", "mongo");
        add("Redis", "redis");
        add("Cosmos DB", "cosmos db", "cosmosdb");
        add("Elasticsearch", "elasticsearch", "opensearch");
        add("Kafka", "kafka");
        add("RabbitMQ", "rabbitmq");
        add("GraphQL", "graphql");
        add("REST", "rest api", "rest apis", "restful", "rest services");
        add("Microservices", "microservices", "microservice");
        add("Hibernate", "hibernate", "jpa");
        add("Maven", "maven");
        add("Gradle", "gradle");
        add("JUnit", "junit");
        add("Docker", "docker");
        add("Kubernetes", "kubernetes", "k8s");
        add("Terraform", "terraform");
        add("AWS", "aws", "amazon web services");
        add("Azure", "azure");
        add("GCP", "gcp", "google cloud");
        add("CI/CD", "ci/cd", "continuous integration", "continuous delivery");
        add("Jenkins", "jenkins");
        add("GitHub Actions", "github actions");
        add("GitLab", "gitlab");
        add("Git", "git");
        add("Linux", "linux");
        add("Machine learning", "machine learning", "ml");
        add("LLM", "llm", "llms", "large language model", "large language models", "generative ai", "genai");
        add("Spring AI", "spring ai");
        add("Data engineering", "data engineering", "data engineer", "etl");
        add("Spark", "spark", "apache spark");
        add("Airflow", "airflow");
        add("Power BI", "power bi");
        add("Tableau", "tableau");
        add("Excel", "microsoft excel", "ms excel", "excel spreadsheets");
        add("Figma", "figma");
        add("UX design", "ux", "user experience", "ux design");
        add("Product management", "product management", "product manager", "product owner");
        add("Agile", "agile", "scrum", "kanban");
        add("Project management", "project management", "project manager");
        add("Security", "cybersecurity", "security", "owasp");
        add("Testing", "test automation", "qa", "quality assurance", "testing");
        add("Selenium", "selenium");
        add("Playwright", "playwright");
        add("HTML", "html");
        add("CSS", "css");
        add("Observability", "observability", "prometheus", "grafana", "opentelemetry");
        add("Sales", "sales", "business development");
        add("Marketing", "marketing", "seo");
        add("Finance", "finance", "accounting");
        add("Customer support", "customer support", "customer service");
        add("Leadership", "team lead", "tech lead", "leadership", "engineering manager");
    }

    private static final Map<String, List<Pattern>> PATTERNS = new LinkedHashMap<>();

    static {
        SKILLS.forEach((name, aliases) -> PATTERNS.put(name, aliases.stream().map(SkillCatalog::wordPattern).toList()));
    }

    private SkillCatalog() {}

    private static void add(String name, String... aliases) { SKILLS.put(name, List.of(aliases)); }

    /** Matches an alias only when it is not glued to other letters, digits, '+' or '#'. */
    private static Pattern wordPattern(String alias) {
        return Pattern.compile("(?<![\\p{L}\\p{N}+#.])" + Pattern.quote(alias) + "(?![\\p{L}\\p{N}+#])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /** The catalog skills mentioned in the text, in catalog order. */
    public static List<String> find(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> found = new ArrayList<>();
        PATTERNS.forEach((name, patterns) -> {
            if (patterns.stream().anyMatch(pattern -> pattern.matcher(text).find())) found.add(name);
        });
        return found;
    }

    /**
     * Whether the text mentions a skill. Skills the person typed themselves and that are not in the
     * catalog are matched by their own spelling.
     */
    public static boolean mentions(String text, String skill) {
        if (text == null || skill == null || skill.isBlank()) return false;
        var patterns = PATTERNS.get(canonical(skill));
        if (patterns != null) return patterns.stream().anyMatch(pattern -> pattern.matcher(text).find());
        return wordPattern(skill.strip()).matcher(text).find();
    }

    /** The catalog's spelling of a skill, or the person's own spelling when the catalog does not know it. */
    public static String canonical(String skill) {
        String clean = skill.strip();
        for (var entry : SKILLS.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(clean)) return entry.getKey();
            for (String alias : entry.getValue()) if (alias.equalsIgnoreCase(clean)) return entry.getKey();
        }
        return clean;
    }

    /** Cleans a list the person typed: trims, uses catalog spellings, drops blanks and duplicates. */
    public static List<String> normalise(List<String> skills) {
        if (skills == null) return List.of();
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String skill : skills) {
            if (skill == null || skill.isBlank()) continue;
            String name = canonical(skill);
            if (name.length() > 60) name = name.substring(0, 60);
            if (seen.add(name.toLowerCase(Locale.ROOT))) result.add(name);
        }
        return result;
    }
}
