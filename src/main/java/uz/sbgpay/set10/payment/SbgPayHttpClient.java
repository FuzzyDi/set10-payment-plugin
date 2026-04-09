package uz.sbgpay.set10.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * HTTP transport for SBG Pay POS API.
 */
final class SbgPayHttpClient {

    /** HTTP 400 threshold for error responses. */
    private static final int HTTP_BAD_REQUEST = 400;
    /** HTTP 401 unauthorized status code. */
    private static final int HTTP_UNAUTHORIZED = 401;
    /** HTTP 429 too many requests status code. */
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    /**
     * Localized message resolver.
     */
    interface Localizer {
        /**
         * Resolves localized message.
         *
         * @param key resource key
         * @param defaultValue fallback value
         * @return localized value
         */
        String get(String key, String defaultValue);
    }

    /** JSON mapper. */
    private final ObjectMapper objectMapper;
    /** Logger instance. */
    private final Logger log;
    /** Device token for API calls. */
    private final String deviceToken;
    /** Default timeout in milliseconds. */
    private final int defaultTimeoutMs;
    /** Localizer instance. */
    private final Localizer localizer;

    /**
     * Creates new transport instance.
     *
     * @param objectMapperValue json mapper
     * @param logValue logger
     * @param deviceTokenValue API token
     * @param defaultTimeoutMsValue default timeout
     * @param localizerValue localization resolver
     */
    SbgPayHttpClient(final ObjectMapper objectMapperValue,
                     final Logger logValue,
                     final String deviceTokenValue,
                     final int defaultTimeoutMsValue,
                     final Localizer localizerValue) {
        this.objectMapper = objectMapperValue;
        this.log = logValue;
        this.deviceToken = deviceTokenValue;
        this.defaultTimeoutMs = defaultTimeoutMsValue;
        this.localizer = localizerValue;
    }

    /**
     * Executes HTTP GET and parses JSON response.
     *
     * @param url target URL
     * @param timeoutMs request timeout
     * @return json response
     * @throws Exception on transport or HTTP error
     */
    JsonNode get(final String url, final int timeoutMs) throws Exception {
        long startTime = System.currentTimeMillis();
        int effectiveTimeoutMs = timeoutMs > 0 ? timeoutMs : defaultTimeoutMs;

        HttpURLConnection conn = (HttpURLConnection) new URL(url)
            .openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Device-Token", deviceToken);
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(effectiveTimeoutMs);
        conn.setReadTimeout(effectiveTimeoutMs);

        try {
            int status = conn.getResponseCode();
            String body = readResponseBody(conn, status);

            long elapsed = System.currentTimeMillis() - startTime;
            log.debug("[HTTP] GET {} -> {} ({}ms, timeout={}ms)",
                url, status, elapsed, effectiveTimeoutMs);

            if (status >= HTTP_BAD_REQUEST) {
                handleHttpError(status, body);
            }

            return objectMapper.readTree(body.isEmpty() ? "{}" : body);
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Executes HTTP POST with JSON body.
     *
     * @param url target URL
     * @param body request body
     * @param idempotencyKey idempotency key
     * @return json response
     * @throws Exception on transport or HTTP error
     */
    JsonNode post(final String url,
                  final Map<String, Object> body,
                  final String idempotencyKey) throws Exception {
        return post(url, body, idempotencyKey, true);
    }

    /**
     * Executes HTTP POST with optional body.
     *
     * @param url target URL
     * @param body request body
     * @param idempotencyKey idempotency key
     * @param withBody true to send JSON body
     * @return json response
     * @throws Exception on transport or HTTP error
     */
    JsonNode post(final String url,
                  final Map<String, Object> body,
                  final String idempotencyKey,
                  final boolean withBody) throws Exception {
        long startTime = System.currentTimeMillis();

        HttpURLConnection conn = (HttpURLConnection) new URL(url)
            .openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Device-Token", deviceToken);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Idempotency-Key", idempotencyKey);
        conn.setConnectTimeout(defaultTimeoutMs);
        conn.setReadTimeout(defaultTimeoutMs);

        if (withBody) {
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            Map<String, Object> effectiveBody = body != null
                ? body : Collections.<String, Object>emptyMap();
            byte[] payload = objectMapper.writeValueAsBytes(effectiveBody);

            log.debug("[HTTP] POST {} Request body: {}",
                url, new String(payload, StandardCharsets.UTF_8));

            try (OutputStream output = conn.getOutputStream()) {
                output.write(payload);
            }
        } else {
            conn.setDoOutput(false);
            log.debug("[HTTP] POST {} Request body: <empty>", url);
        }

        try {
            int status = conn.getResponseCode();
            String responseBody = readResponseBody(conn, status);

            long elapsed = System.currentTimeMillis() - startTime;
            log.debug("[HTTP] POST {} -> {} ({}ms)", url, status, elapsed);
            log.debug("[HTTP] POST {} Response body: {}", url, responseBody);

            if (status >= HTTP_BAD_REQUEST) {
                handleHttpError(status, responseBody);
            }

            return objectMapper.readTree(
                responseBody.isEmpty() ? "{}" : responseBody
            );
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Reads response body from input or error stream.
     *
     * @param conn connection
     * @param status HTTP status
     * @return response body
     * @throws Exception if read fails
     */
    private String readResponseBody(final HttpURLConnection conn,
                                    final int status) throws Exception {
        InputStream stream = status < HTTP_BAD_REQUEST
            ? conn.getInputStream()
            : conn.getErrorStream();
        if (stream == null) {
            return "";
        }

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            return content.toString();
        }
    }

    /**
     * Converts non-2xx response to domain exception.
     *
     * @param status HTTP status
     * @param body response body
     * @throws Exception formatted exception
     */
    private void handleHttpError(final int status,
                                 final String body) throws Exception {
        String detail = "HTTP " + status;
        String title = null;

        try {
            JsonNode error = objectMapper.readTree(body);
            if (error.has("detail")) {
                detail = error.get("detail").asText();
            } else if (error.has("title")) {
                detail = error.get("title").asText();
            }
            if (error.has("title")) {
                title = error.get("title").asText();
            }
        } catch (IOException ignored) {
            log.debug("[HTTP] Failed to parse error body: {}", body);
        }

        if (status == HTTP_UNAUTHORIZED) {
            throw new Exception(localizer.get("error.unauthorized",
                "Ошибка авторизации: Проверьте корректность Device-Token"));
        }
        if (status == HTTP_TOO_MANY_REQUESTS) {
            throw new Exception(localizer.get("error.rate.limit",
                "Слишком много запросов, попробуйте позже"));
        }
        throw new HttpStatusException(status, title, detail);
    }

    /**
     * HTTP exception preserving status and parsed fields.
     */
    static final class HttpStatusException extends Exception {

        /** HTTP status code. */
        private final int status;
        /** Parsed error title. */
        private final String title;
        /** Parsed error detail. */
        private final String detail;

        /**
         * Creates status exception.
         *
         * @param statusCode HTTP status code
         * @param errorTitle error title
         * @param errorDetail error details
         */
        HttpStatusException(final int statusCode,
                            final String errorTitle,
                            final String errorDetail) {
            super(errorDetail != null ? errorDetail : "HTTP " + statusCode);
            this.status = statusCode;
            this.title = errorTitle;
            this.detail = errorDetail;
        }

        /**
         * Returns status.
         *
         * @return status
         */
        int getStatus() {
            return status;
        }

        /**
         * Returns title.
         *
         * @return title
         */
        String getTitle() {
            return title;
        }

        /**
         * Returns detail.
         *
         * @return detail
         */
        String getDetail() {
            return detail;
        }
    }
}
