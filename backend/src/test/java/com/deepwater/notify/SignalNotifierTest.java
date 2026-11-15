package com.deepwater.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SignalNotifierTest {
    @Test
    void postsTheMessageToTheSignalRestService() throws Exception {
        var path = new AtomicReference<String>();
        var body = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        server.start();
        try {
            new SignalNotifier("http://127.0.0.1:" + server.getAddress().getPort() + "/", "+4511111111", List.of("+4522222222"))
                    .send("Hello owner");
        } finally {
            server.stop(0);
        }
        assertThat(path.get()).isEqualTo("/v2/send");
        JsonNode sent = new ObjectMapper().readTree(body.get());
        assertThat(sent.get("number").asText()).isEqualTo("+4511111111");
        assertThat(sent.get("recipients").get(0).asText()).isEqualTo("+4522222222");
        assertThat(sent.get("message").asText()).isEqualTo("Hello owner");
    }

    @Test
    void isOffWithoutSettingsAndNeverThrows() {
        var notifier = new SignalNotifier("", "", List.of());
        assertThat(notifier.isConfigured()).isFalse();
        assertThatNoException().isThrownBy(() -> notifier.send("x"));
        assertThatNoException().isThrownBy(() -> new SignalNotifier("http://127.0.0.1:1", "+1", List.of("+2")).send("x"));
    }
}
