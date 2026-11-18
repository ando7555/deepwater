package com.deepwater.tools;

import com.deepwater.amber.AmberService;
import com.deepwater.echo.EchoCardService;
import com.deepwater.echo.EchoService;
import com.deepwater.exam.ExamContent;
import com.deepwater.exam.ExamService;
import com.deepwater.harbor.ApplicationService;
import com.deepwater.harbor.JobSearchService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/**
 * The tools an AI may call on behalf of the signed-in person. One registry serves two doors: the MCP server
 * ({@code /mcp}, for outside assistants such as Claude) and Deepwater's own assistant ({@code /api/v1/assistant}).
 * Every call carries the account id resolved from the person's own session token, so a tool can only reach that
 * person's data; there is no way to pass another account id in the arguments.
 * <p>
 * Deliberately left out of this first version: anything about other Amber members (suggestions, invitations,
 * messages), because sending other people's dating data to an outside AI assistant needs their consent; and
 * anything destructive (deleting cards, leaving Amber, deleting the account).
 */
@Component
public class DeepwaterTools {
    /** One tool: name, what it does (the assistant reads this), JSON Schema of its arguments, and the code. */
    public record Tool(String name, String title, String description, Map<String, Object> inputSchema, boolean readOnly,
                BiFunction<String, Map<String, Object>, Object> handler) {
        public Map<String, Object> descriptor() {
            return Map.of("name", name, "title", title, "description", description, "inputSchema", inputSchema,
                    "annotations", Map.of("readOnlyHint", readOnly, "destructiveHint", false, "openWorldHint", false));
        }
    }

    private static final int MAX_JOBS = 20;
    private static final int DESCRIPTION_CHARS = 400;
    private static final String ATTEMPTS = "attempts";
    private static final Logger log = LoggerFactory.getLogger(DeepwaterTools.class);
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final ObjectMapper json;

    DeepwaterTools(EchoService echo, EchoCardService cards, JobSearchService jobs, ApplicationService applications,
             ExamService exams, AmberService amber, ObjectMapper json,
             @Value("${deepwater.tools.enabled:}") String enabled) {
        this.json = json;
        add(new Tool("echo_overview", "Echo learning overview",
                "The person's Echo language-learning state: chosen language, goal, the suggested next step, "
                        + "answer totals, and each lesson with its mastery (0 to 1) and whether it is due for review.",
                schema(Map.of(), List.of()), true,
                (account, args) -> overview(echo.home(account))));
        add(new Tool("echo_cards", "Echo phrase cards",
                "The person's phrase cards for their current Echo language, with Leitner box and next review time. "
                        + "By default only the cards due now.",
                schema(Map.of("dueOnly", prop("boolean", "Only cards due for review now. Default true.")), List.of()), true,
                (account, args) -> {
                    boolean dueOnly = !Boolean.FALSE.equals(bool(args, "dueOnly"));
                    var list = cards.cards(account).stream().filter(c -> !dueOnly || c.due()).toList();
                    return Map.of("count", list.size(), "cards", list);
                }));
        add(new Tool("echo_add_card", "Add an Echo phrase card",
                "Adds one phrase card to the person's deck for their current Echo language. Use it only when the "
                        + "person asked for the card. front is the phrase in the language being learned; back is its meaning.",
                schema(Map.of("front", prop("string", "The phrase in the language being learned, for example \"en kop kaffe\"."),
                        "back", prop("string", "What it means, for example \"a cup of coffee\".")), List.of("front", "back")), false,
                (account, args) -> cards.save(account, text(args, "front"), text(args, "back"), null)));
        add(new Tool("harbor_find_jobs", "Find matching jobs in Harbor",
                "Jobs ranked by a 0-100 match score against the person's Harbor talent profile (built from their CV), "
                        + "with the matched and missing skills and the work-permit situation for each.",
                schema(Map.of(
                        "query", prop("string", "Words to look for in the title, company or description."),
                        "country", prop("string", "Two-letter country code, for example DK, SE, NO, CH, DE."),
                        "visa", Map.of("type", "string", "enum", List.of("ANY", "SPONSORED", "NO_PERMIT_NEEDED", "OPEN_TO_ME", "NOT_RULED_OUT"),
                                "description", "Work-permit filter. OPEN_TO_ME = no permit needed or the posting offers sponsorship."),
                        "remote", Map.of("type", "string", "enum", List.of("ANY", "REMOTE", "ONSITE")),
                        "localLanguageNotRequired", prop("boolean", "Hide postings that require the local language."),
                        "minScore", Map.of("type", "integer", "minimum", 0, "maximum", 100),
                        "max", Map.of("type", "integer", "minimum", 1, "maximum", MAX_JOBS, "description", "How many jobs to return. Default 10.")),
                        List.of()), true,
                (account, args) -> findJobs(jobs, account, args)));
        add(new Tool("harbor_applications", "Harbor application tracker",
                "The jobs the person is tracking in Harbor and where each application stands.",
                schema(Map.of(), List.of()), true,
                (account, args) -> Map.of("applications", applications.list(account))));
        add(new Tool("polish_b1_results", "Polish B1 practice results",
                "The person's Polish B1 practice exam attempts, newest first: average percent, level estimate and "
                        + "whether the examiner judged them ready for B1.",
                schema(Map.of(), List.of()), true,
                (account, args) -> Map.of(ATTEMPTS, exams.history(account, ExamContent.POLISH_B1))));
        add(new Tool("danish_pd3_results", "Danish PD3 practice results",
                "The person's Danish practice exam attempts (in the shape of Prøve i Dansk 3, level B2), newest first: "
                        + "average percent, level estimate and whether the examiner judged them ready for PD3.",
                schema(Map.of(), List.of()), true,
                (account, args) -> Map.of(ATTEMPTS, exams.history(account, ExamContent.DANISH_PD3))));
        add(new Tool("german_b1_results", "German B1 practice results",
                "The person's German B1 practice exam attempts (in the shape of the Goethe-Zertifikat B1), newest first: "
                        + "average percent, level estimate and whether the examiner judged them ready for B1.",
                schema(Map.of(), List.of()), true,
                (account, args) -> Map.of(ATTEMPTS, exams.history(account, ExamContent.GERMAN_B1))));
        add(new Tool("amber_my_profile", "My Amber profile",
                "The person's own Amber profile, or joined=false if they have not joined Amber. "
                        + "Never returns anything about other members.",
                schema(Map.of(), List.of()), true,
                (account, args) -> amber.profile(account)));
        keepOnly(enabled);
    }

    /** The result of one call: the JSON (or the reason it failed) as text, ready to hand to a model. */
    public record Outcome(String text, boolean error) {}

    public List<Tool> all() { return List.copyOf(tools.values()); }

    public List<Map<String, Object>> list() {
        return tools.values().stream().map(Tool::descriptor).toList();
    }

    public Tool find(String name) {
        return tools.get(name);
    }

    /**
     * Runs a tool as {@code accountId}. A business rule that says no (for example "set up your Echo profile
     * first") comes back as an error outcome the model can explain; unexpected failures are logged, not leaked.
     */
    public Outcome call(String accountId, String name, Map<String, Object> args) {
        Tool tool = tools.get(name);
        if (tool == null) return new Outcome("Unknown tool: " + name, true);
        try {
            return new Outcome(json.writeValueAsString(tool.handler().apply(accountId, args == null ? Map.of() : args)), false);
        } catch (ResponseStatusException ex) {
            return new Outcome(ex.getReason() == null ? ex.getStatusCode().toString() : ex.getReason(), true);
        } catch (RuntimeException ex) {
            log.warn("Tool {} failed", name, ex);
            return new Outcome("The tool failed on the Deepwater side. Try again later.", true);
        }
    }

    private void add(Tool tool) { tools.put(tool.name(), tool); }

    /** deepwater.tools.enabled (comma-separated names) switches tools off without code changes; empty = all. */
    private void keepOnly(String enabled) {
        if (enabled == null || enabled.isBlank()) return;
        var wanted = java.util.Arrays.stream(enabled.split(",")).map(String::strip).filter(n -> !n.isEmpty()).toList();
        var unknown = wanted.stream().filter(n -> !tools.containsKey(n)).toList();
        if (!unknown.isEmpty()) throw new IllegalStateException("deepwater.tools.enabled names unknown tools: " + unknown);
        tools.keySet().retainAll(wanted);
    }

    /** The home screen without the full exercise lists, which an assistant does not need. */
    private static Map<String, Object> overview(EchoService.EchoHome home) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("onboarded", home.onboarded());
        out.put("profile", home.profile());
        out.put("nextAction", home.nextAction());
        out.put("totalAttempts", home.totalAttempts());
        out.put("correctAttempts", home.correctAttempts());
        out.put("lessons", home.lessons().stream().map(l -> {
            Map<String, Object> lesson = new LinkedHashMap<>();
            lesson.put("id", l.id());
            lesson.put("title", l.title());
            lesson.put("level", l.level());
            lesson.put("topic", l.topic());
            lesson.put("mastery", l.mastery());
            lesson.put(ATTEMPTS, l.attempts());
            lesson.put("due", l.due());
            lesson.put("nextReviewAt", l.nextReviewAt());
            return lesson;
        }).toList());
        return out;
    }

    private static Object findJobs(JobSearchService jobs, String account, Map<String, Object> args) {
        Integer max = integer(args, "max");
        var filters = new JobSearchService.Filters(optional(args, "query"), optional(args, "country"), optional(args, "remote"),
                optional(args, "visa"), integer(args, "minScore"), null, max == null ? 10 : Math.clamp(max, 1, MAX_JOBS),
                null, Boolean.TRUE.equals(bool(args, "localLanguageNotRequired")) ? "NO_LOCAL_REQUIRED" : null);
        var result = jobs.search(account, filters);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("profileHasNoSkills", result.profileIsEmpty());
        out.put("totalJobs", result.totalJobs());
        out.put("jobs", result.jobs().stream().map(m -> {
            var j = m.job();
            Map<String, Object> job = new LinkedHashMap<>();
            job.put("id", j.id());
            job.put("title", j.title());
            job.put("company", j.company());
            job.put("country", j.countryName());
            job.put("location", j.location());
            job.put("remote", j.remote());
            job.put("url", j.url());
            if (j.hiringProcess() != null) job.put("hiringProcess", j.hiringProcess());
            job.put("summary", j.description() == null || j.description().length() <= DESCRIPTION_CHARS
                    ? j.description() : j.description().substring(0, DESCRIPTION_CHARS) + "…");
            job.put("score", m.match().score());
            job.put("matchedSkills", m.match().matchedSkills());
            job.put("missingSkills", m.match().missingSkills());
            job.put("reasons", m.match().reasons());
            job.put("workPermit", m.match().workPermit());
            job.put("applicationStatus", m.applicationStatus());
            return job;
        }).toList());
        return out;
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> prop(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    private static String text(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (!(value instanceof String s) || s.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "\"" + name + "\" is required.");
        return s;
    }

    private static String optional(Map<String, Object> args, String name) {
        return args.get(name) instanceof String s && !s.isBlank() ? s : null;
    }

    private static Integer integer(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String text && text.strip().matches("-?\\d{1,9}")) return Integer.valueOf(text.strip());
        return null; // small local models sometimes send numbers as text
    }

    private static Boolean bool(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value instanceof Boolean b) return b;
        if (value instanceof String text && (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false"))) return Boolean.valueOf(text);
        return null;
    }
}
