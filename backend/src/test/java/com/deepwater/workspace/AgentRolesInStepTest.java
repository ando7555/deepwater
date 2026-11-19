package com.deepwater.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * The roles live twice: as data for the workspace screen (workspace/roles.json) and as Claude Code agents in the
 * deepwater-developer plugin. This test keeps them in step, so a role cannot quietly exist in only one place.
 */
class AgentRolesInStepTest {
    @Test
    void everyWorkspaceRoleHasADeepwaterDeveloperAgentWithTheSameNameAndTools() throws Exception {
        AgentRoles roles = new AgentRoles(new ObjectMapper());
        Path agents = Path.of("..", "deepwater-developer", "agents");
        assertThat(roles.all()).hasSize(5);
        for (AgentRoles.AgentRole role : roles.all()) {
            Path file = agents.resolve(role.id() + ".md");
            assertThat(file).as("agent file for " + role.id()).exists();
            String text = Files.readString(file);
            assertThat(text).as(file.toString()).contains("name: " + role.id());
            for (String tool : role.tools()) assertThat(text).as(file + " lists " + tool).contains(tool);
            assertThat(text).as(file + " must say it never approves").contains("never approve");
        }
    }
}
