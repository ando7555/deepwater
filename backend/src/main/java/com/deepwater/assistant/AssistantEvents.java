package com.deepwater.assistant;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The events one assistant run streams to the screen, named after the AG-UI protocol (an open format for
 * agent-to-UI streaming) so a standard AG-UI client could read them: RUN_STARTED, TEXT_MESSAGE_*, TOOL_CALL_*,
 * RUN_FINISHED and RUN_ERROR. Each event is a flat JSON object with a "type".
 */
final class AssistantEvents {
    private AssistantEvents() {}

    static Map<String, Object> event(String type, Object... keyValues) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        for (int i = 0; i + 1 < keyValues.length; i += 2) event.put((String) keyValues[i], keyValues[i + 1]);
        return event;
    }
}
