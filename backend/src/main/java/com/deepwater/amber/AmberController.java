package com.deepwater.amber;

import com.deepwater.platform.PlatformService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Amber's REST API. Same Deepwater sign-in as Echo and Harbor, but nothing here works until the person
 * has created an Amber profile, and other members are addressed only by their Amber member id.
 */
@RestController
@RequestMapping("/api/v1/amber")
public class AmberController {
    private static final String AUTH = "Authorization";
    private final PlatformService platform;
    private final AmberService amber;
    private final AmberConnections connections;

    public AmberController(PlatformService platform, AmberService amber, AmberConnections connections) {
        this.platform = platform;
        this.amber = amber;
        this.connections = connections;
    }

    @GetMapping("/profile")
    AmberService.MyProfile profile(@RequestHeader(value = AUTH, required = false) String auth) {
        return amber.profile(platform.requireAccount(auth));
    }

    @PutMapping("/profile")
    AmberService.MyProfile save(@RequestHeader(value = AUTH, required = false) String auth, @RequestBody AmberService.ProfileInput body) {
        return amber.save(platform.requireAccount(auth), body);
    }

    /** Leave Amber. The Deepwater account, Echo and Harbor stay. */
    @DeleteMapping("/profile")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave(@RequestHeader(value = AUTH, required = false) String auth) {
        amber.leave(platform.requireAccount(auth));
    }

    @GetMapping("/suggestions")
    List<AmberService.Suggestion> suggestions(@RequestHeader(value = AUTH, required = false) String auth) {
        return amber.suggestions(platform.requireAccount(auth));
    }

    /** Not now: the person comes back in suggestions after 30 days. They are never told. */
    @PostMapping("/suggestions/{memberId}/pass")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void pass(@RequestHeader(value = AUTH, required = false) String auth, @PathVariable String memberId) {
        amber.decide(platform.requireAccount(auth), memberId, "PASS");
    }

    /** Never suggest this person again. They are never told. */
    @PostMapping("/suggestions/{memberId}/hide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void hide(@RequestHeader(value = AUTH, required = false) String auth, @PathVariable String memberId) {
        amber.decide(platform.requireAccount(auth), memberId, "HIDE");
    }

    @PostMapping("/invitations")
    AmberConnections.InvitationView invite(@RequestHeader(value = AUTH, required = false) String auth, @RequestBody MemberRequest body) {
        return connections.invite(platform.requireAccount(auth), body.memberId());
    }

    @GetMapping("/invitations")
    AmberConnections.Invitations invitations(@RequestHeader(value = AUTH, required = false) String auth) {
        return connections.invitations(platform.requireAccount(auth));
    }

    @PostMapping("/invitations/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void accept(@RequestHeader(value = AUTH, required = false) String auth, @PathVariable String id) {
        connections.respond(platform.requireAccount(auth), id, true);
    }

    @PostMapping("/invitations/{id}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void decline(@RequestHeader(value = AUTH, required = false) String auth, @PathVariable String id) {
        connections.respond(platform.requireAccount(auth), id, false);
    }

    @PostMapping("/invitations/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void withdraw(@RequestHeader(value = AUTH, required = false) String auth, @PathVariable String id) {
        connections.withdraw(platform.requireAccount(auth), id);
    }

    @GetMapping("/connections")
    List<AmberConnections.ConnectionView> connections(@RequestHeader(value = AUTH, required = false) String auth) {
        return connections.connections(platform.requireAccount(auth));
    }

    @GetMapping("/connections/{id}/messages")
    List<AmberConnections.Message> messages(@RequestHeader(value = AUTH, required = false) String auth, @PathVariable String id) {
        return connections.messages(platform.requireAccount(auth), id);
    }

    @PostMapping("/connections/{id}/messages")
    AmberConnections.Message send(@RequestHeader(value = AUTH, required = false) String auth, @PathVariable String id,
                                  @RequestBody MessageRequest body) {
        return connections.send(platform.requireAccount(auth), id, body.body());
    }

    @PostMapping("/connections/{id}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leaveConnection(@RequestHeader(value = AUTH, required = false) String auth, @PathVariable String id) {
        connections.leave(platform.requireAccount(auth), id);
    }

    @PostMapping("/safety/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void block(@RequestHeader(value = AUTH, required = false) String auth, @RequestBody MemberRequest body) {
        amber.block(platform.requireAccount(auth), body.memberId());
    }

    @PostMapping("/safety/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void report(@RequestHeader(value = AUTH, required = false) String auth, @RequestBody ReportRequest body) {
        amber.report(platform.requireAccount(auth), body.memberId(), body.reason(), body.note());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> problem(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason()));
    }

    record MemberRequest(String memberId) {}
    record MessageRequest(String body) {}
    record ReportRequest(String memberId, String reason, String note) {}
}
