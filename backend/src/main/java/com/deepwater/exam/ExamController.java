package com.deepwater.exam;

import com.deepwater.platform.PlatformService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The Polish B1 practice exam: the tasks (without answers), marking an attempt, and the learner's past attempts. */
@RestController
@RequestMapping("/api/v1/exam/polish-b1")
public class ExamController {
    private static final String AUTHORIZATION = "Authorization";
    private final PlatformService platform;
    private final ExamContent content;
    private final ExamService exams;

    public ExamController(PlatformService platform, ExamContent content, ExamService exams) {
        this.platform = platform;
        this.content = content;
        this.exams = exams;
    }

    @GetMapping
    Map<String, Object> exam(@RequestHeader(value = AUTHORIZATION, required = false) String auth) {
        platform.requireAccount(auth);
        return Map.of("exam", content.publicExam(), "passPercent", exams.passPercent(), "aiExaminer", exams.aiExaminer());
    }

    @PostMapping("/attempts")
    ExamService.Result submit(@RequestHeader(value = AUTHORIZATION, required = false) String auth, @RequestBody Submission body) {
        return exams.submit(platform.requireAccount(auth), body == null ? null : body.answers());
    }

    @GetMapping("/attempts")
    List<ExamService.AttemptSummary> history(@RequestHeader(value = AUTHORIZATION, required = false) String auth) {
        return exams.history(platform.requireAccount(auth));
    }

    @GetMapping("/attempts/{id}")
    ExamService.Result attempt(@RequestHeader(value = AUTHORIZATION, required = false) String auth, @PathVariable String id) {
        return exams.attempt(platform.requireAccount(auth), id);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> problem(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason()));
    }

    /** answers maps an item id to the chosen option index (as text) or to the written or spoken answer. */
    record Submission(Map<String, String> answers) {}
}
