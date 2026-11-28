package com.deepwater.assistant;

import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Reads a model's tool-call arguments (a JSON object, or a JSON object written as a string) into plain values. */
final class ToolArguments {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ToolArguments() {}

    static Map<String, Object> read(JsonNode node) {
        JsonNode parsed = node;
        if (node.isString()) {
            String text = node.asString();
            parsed = text.isBlank() ? JSON.createObjectNode() : JSON.readTree(text);
        }
        Map<String, Object> args = new LinkedHashMap<>();
        if (parsed.isObject()) parsed.properties().forEach(e -> args.put(e.getKey(), value(e.getValue())));
        return args;
    }

    /** A JSON object as a plain map, for sending a model's own message back unchanged. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(JsonNode node) { return JSON.convertValue(node, Map.class); }

    static String write(Map<String, Object> input) { return JSON.writeValueAsString(input); }

    private static Object value(JsonNode v) {
        if (v.isBoolean()) return v.asBoolean();
        if (v.isIntegralNumber()) return v.asLong();
        if (v.isNumber()) return v.asDouble();
        if (v.isString()) return v.asString();
        if (v.isArray()) {
            java.util.List<Object> items = new java.util.ArrayList<>();
            v.forEach(item -> items.add(value(item)));
            return items;
        }
        if (v.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            v.properties().forEach(e -> map.put(e.getKey(), value(e.getValue())));
            return map;
        }
        return v.isNull() ? null : v.toString();
    }
}
