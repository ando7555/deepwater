package com.deepwater.workspace;

import com.deepwater.platform.PlatformService;
import com.deepwater.workspace.WorkspaceService.Actor;
import com.deepwater.workspace.WorkspaceService.TaskInput;
import com.deepwater.workspace.WorkspaceService.TaskView;
import java.util.List;
import java.util.Map;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The owner's AI workspace screen. Every move here is the owner's own, so this is the only door that can approve,
 * reject, accept or send back. AI roles reach the workspace through the MCP tools instead (see DeepwaterTools).
 */
@RestController
@RequestMapping("/api/v1/workspace")
public class WorkspaceController {
    private static final String AUTH = "Authorization";
    private final PlatformService platform;
    private final WorkspaceService workspace;
    private final AgentRoles roles;
    private final WorkspaceVoice voice;

    public WorkspaceController(PlatformService platform, WorkspaceService workspace, AgentRoles roles, WorkspaceVoice voice) {
        this.platform = platform;
        this.workspace = workspace;
        this.roles = roles;
        this.voice = voice;
    }

    /**
     * One turn in the voice room. The browser sends what the owner said (as text, from speech recognition) with the
     * recent conversation; the role answers in a few spoken sentences, which the browser reads aloud.
     */
    @PostMapping("/roles/{roleId}/talk")
    WorkspaceVoice.Reply talk(@RequestHeader(value = AUTH, required = false) String auth, @PathVariable String roleId,
                              @RequestBody TalkRequest body) {
        return voice.talk(platform.requireAccount(auth), roleId, body == null ? null : body.messages(),
                body == null ? null : body.text());
    }

    /** {@code text}: what the owner just said, continuing the saved conversation. {@code messages}: a full context instead. */
    record TalkRequest(List<com.deepwater.assistant.AssistantService.ChatMessage> messages, String text) {}

    /** The saved conversation with one role, so it can be continued later. */
    @GetMapping("/roles/{roleId}/talk")
    Map<String, Object> conversation(@RequestHeader(value = AUTH, required = false) String auth, @PathVariable String roleId) {
        return Map.of("turns", voice.conversation(platform.requireAccount(auth), roleId));
    }

    /** Starts a new conversation with one role. */
    @org.springframework.web.bind.annotation.DeleteMapping("/roles/{roleId}/talk")
    org.springframework.http.ResponseEntity<Void> forget(@RequestHeader(value = AUTH, required = false) String auth, @PathVariable String roleId) {
        voice.forget(platform.requireAccount(auth), roleId);
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    /** Whether the signed-in account may open the workspace; the app shows the entry point only when true. */
    @GetMapping("/access")
    Map<String, Object> access(@RequestHeader(value = AUTH, required = false) String auth) {
        return Map.of("owner", workspace.isOwner(platform.requireAccount(auth)));
    }

    @GetMapping("/roles")
    List<AgentRoles.AgentRole> roles(@RequestHeader(value = AUTH, required = false) String auth) {
        workspace.requireOwner(platform.requireAccount(auth));
        return roles.all();
    }

    @GetMapping("/tasks")
    List<TaskView> tasks(@RequestHeader(value = AUTH, required = false) String auth, @RequestParam(required = false) String status) {
        return workspace.tasks(platform.requireAccount(auth), status);
    }

    @GetMapping("/tasks/{id}")
    TaskView task(@RequestHeader(value = AUTH, required = false) String auth, @PathVariable String id) {
        return workspace.task(platform.requireAccount(auth), id);
    }

    @PostMapping("/tasks")
    TaskView propose(@RequestHeader(value = AUTH, required = false) String auth, @RequestBody TaskInput body) {
        return workspace.propose(platform.requireAccount(auth), Actor.owner(), body);
    }

    @PutMapping("/tasks/{id}")
    TaskView edit(@RequestHeader(value = AUTH, required = false) String auth, @PathVariable String id, @RequestBody EditRequest body) {
        return workspace.edit(platform.requireAccount(auth), id, version(body.version()), body.task());
    }

    @PostMapping("/tasks/{id}/approve")
    TaskView approve(@RequestHeader(value = AUTH, required = false) String auth, @PathVariable String id, @RequestBody MoveRequest body) {
        return workspace.approve(platform.requireAccount(auth), id, version(body.version()));
    }

    @PostMapping("/tasks/{id}/reject")
    TaskView reject(@RequestHeader(value = AUTH, required = false) String auth, @PathVariable String id, @RequestBody MoveRequest body) {
        return workspace.reject(platform.requireAccount(auth), id, version(body.version()), body.note());
    }

    @PostMapping("/tasks/{id}/start")
    TaskView start(@RequestHeader(value = AUTH, required = false) String auth, @PathVariable String id) {
        return workspace.start(platform.requireAccount(auth), Actor.owner(), id);
    }

    @PostMapping("/tasks/{id}/evidence")
    TaskView evidence(@RequestHeader(value = AUTH, required = false) String auth, @PathVariable String id, @RequestBody MoveRequest body) {
        return workspace.submitEvidence(platform.requireAccount(auth), Actor.owner(), id, body.note());
    }

    @PostMapping("/tasks/{id}/accept")
    TaskView accept(@RequestHeader(value = AUTH, required = false) String auth, @PathVariable String id, @RequestBody MoveRequest body) {
        return workspace.accept(platform.requireAccount(auth), id, version(body.version()));
    }

    @PostMapping("/tasks/{id}/send-back")
    TaskView sendBack(@RequestHeader(value = AUTH, required = false) String auth, @PathVariable String id, @RequestBody MoveRequest body) {
        return workspace.sendBack(platform.requireAccount(auth), id, version(body.version()), body.note());
    }

    record MoveRequest(Integer version, String note) {}

    record EditRequest(Integer version, TaskInput task) {}

    private static int version(Integer version) {
        if (version == null) throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                "Send the task version you saw, so a stale screen cannot overwrite a newer change.");
        return version;
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> problem(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason()));
    }
}
