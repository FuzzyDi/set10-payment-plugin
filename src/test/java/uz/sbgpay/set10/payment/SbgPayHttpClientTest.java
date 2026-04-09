package uz.sbgpay.set10.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SbgPayHttpClientTest {

    @Test
    void get_returnsJsonPayload() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/ok", exchange ->
            respond(exchange, 200, "{\"status\":\"ok\"}"));
        server.start();
        try {
            SbgPayHttpClient client = newClient();
            String url = "http://127.0.0.1:" + server.getAddress().getPort()
                + "/ok";

            JsonNode response = client.get(url, 1500);

            assertEquals("ok", response.get("status").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void post_throwsHttpStatusExceptionOnConflict() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/conflict", exchange -> respond(
            exchange,
            409,
            "{\"title\":\"invalid_status\","
                + "\"detail\":\"Cannot complete payment\"}"
        ));
        server.start();
        try {
            SbgPayHttpClient client = newClient();
            String url = "http://127.0.0.1:" + server.getAddress().getPort()
                + "/conflict";

            SbgPayHttpClient.HttpStatusException error = assertThrows(
                SbgPayHttpClient.HttpStatusException.class,
                () -> client.post(
                    url,
                    Collections.<String, Object>emptyMap(),
                    "idem-1"
                )
            );

            assertEquals(409, error.getStatus());
            assertEquals("invalid_status", error.getTitle());
            assertEquals("Cannot complete payment", error.getDetail());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void get_mapsUnauthorizedToLocalizedMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/unauthorized", exchange ->
            respond(exchange, 401, "{\"detail\":\"bad token\"}"));
        server.start();
        try {
            SbgPayHttpClient client = newClient();
            String url = "http://127.0.0.1:" + server.getAddress().getPort()
                + "/unauthorized";

            Exception error = assertThrows(
                Exception.class,
                () -> client.get(url, 1500)
            );
            assertEquals("AUTH_MESSAGE", error.getMessage());
        } finally {
            server.stop(0);
        }
    }

    private SbgPayHttpClient newClient() {
        return new SbgPayHttpClient(
            new ObjectMapper(),
            LoggerFactory.getLogger(SbgPayHttpClientTest.class),
            "token",
            2000,
            (key, defaultValue) -> {
                if ("error.unauthorized".equals(key)) {
                    return "AUTH_MESSAGE";
                }
                if ("error.rate.limit".equals(key)) {
                    return "RATE_LIMIT_MESSAGE";
                }
                return defaultValue;
            }
        );
    }

    private static void respond(final HttpExchange exchange,
                                final int status,
                                final String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(payload);
        } finally {
            exchange.close();
        }
    }
}

