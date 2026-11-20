package com.deepwater.workspace;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * The owner's AI roles as contracts: what each role is for, what it does and never does, what it hands back and
 * which workspace tools it may use. Read once from {@code workspace/roles.json} (source: {@code ai-workspace/roles.json}). The same roles exist as Claude Code
 * agents in {@code ai-workspace/deepwater-developer/agents/<id>.md}; a test keeps the two in step.
 * <p>
 * No role can approve: approving is a human step done in the workspace screen, and no tool offers it.
 */
@Component
public class AgentRoles {
    public record AgentRole(String id, String name, String purpose, List<String> does, List<String> doesNot,
                            List<String> handsBack, List<String> tools) {}

    private final List<AgentRole> roles;

    public AgentRoles(ObjectMapper json) {
        try (InputStream in = AgentRoles.class.getResourceAsStream("/workspace/roles.json")) {
            if (in == null) throw new IllegalStateException("workspace/roles.json is missing");
            roles = List.copyOf(Arrays.asList(json.readValue(in, AgentRole[].class)));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        for (AgentRole role : roles) {
            if (role.tools().stream().anyMatch(t -> t.contains("approve")))
                throw new IllegalStateException("Role " + role.id() + " must not have an approve tool");
        }
    }

    public List<AgentRole> all() { return roles; }

    public Optional<AgentRole> find(String id) {
        return roles.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    /** True when the role's contract lists the tool. */
    public boolean allows(String roleId, String tool) {
        return find(roleId).map(r -> r.tools().contains(tool)).orElse(false);
    }
}
