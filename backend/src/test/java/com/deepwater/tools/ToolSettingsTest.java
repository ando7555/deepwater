package com.deepwater.tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Tools can be switched off by setting, for both the assistant and MCP, with no code change. */
@SpringBootTest(properties = "deepwater.tools.enabled=echo_overview, harbor_find_jobs")
class ToolSettingsTest {
    @Autowired DeepwaterTools tools;

    @Test
    void onlyTheToolsNamedInSettingsAreOffered() {
        assertThat(tools.all()).extracting(DeepwaterTools.Tool::name).containsExactly("echo_overview", "harbor_find_jobs");
        assertThat(tools.call("anyone", "echo_add_card", java.util.Map.of()).error()).isTrue();
    }
}
