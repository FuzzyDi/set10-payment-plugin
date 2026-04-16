package uz.sbgpay.set10.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SbgPayStatusMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapPaymentStatus_mapsAllFields() throws Exception {
        JsonNode response = objectMapper.readTree("{"
            + "\"paymentId\":\"p1\","
            + "\"paymentCode\":\"pay_abc\","
            + "\"status\":\"completed\","
            + "\"qrPayload\":\"payload\","
            + "\"qrCodeData\":\"base64\","
            + "\"errorMessage\":\"none\""
            + "}");

        SbgPayStatusMapper.PaymentStatusPayload payload =
            SbgPayStatusMapper.mapPaymentStatus(response);

        assertEquals("p1", payload.getPaymentId());
        assertEquals("pay_abc", payload.getPaymentCode());
        assertEquals("completed", payload.getStatus());
        assertEquals("payload", payload.getQrPayload());
        assertEquals("base64", payload.getQrCodeData());
        assertEquals("none", payload.getErrorMessage());
    }

    @Test
    void mapPaymentStatus_fallsBackToErrorAndMessage() throws Exception {
        JsonNode response = objectMapper.readTree("{"
            + "\"paymentId\":\"p2\","
            + "\"status\":\"failed\","
            + "\"message\":\"timeout\""
            + "}");

        SbgPayStatusMapper.PaymentStatusPayload payload =
            SbgPayStatusMapper.mapPaymentStatus(response);

        assertEquals("p2", payload.getPaymentId());
        assertEquals("failed", payload.getStatus());
        assertEquals("timeout", payload.getErrorMessage());
        assertNull(payload.getPaymentCode());
        assertNull(payload.getQrPayload());
    }

    @Test
    void mapPaymentStatus_mapsProcessingDataAndLoyalty() throws Exception {
        JsonNode response = objectMapper.readTree("{"
            + "\"paymentId\":\"p3\","
            + "\"status\":\"completed\","
            + "\"processingData\":{"
            + "\"rrn\":\"123456789012\","
            + "\"stan\":\"654321\""
            + "},"
            + "\"loyalty\":{"
            + "\"qrPayload\":\"https://example.org\","
            + "\"qrCodeData\":\"base64-loyalty\","
            + "\"text\":\"Coupon text\""
            + "}"
            + "}");

        SbgPayStatusMapper.PaymentStatusPayload payload =
            SbgPayStatusMapper.mapPaymentStatus(response);

        Map<String, String> processing = payload.getProcessingData();
        assertEquals(2, processing.size());
        assertEquals("123456789012", processing.get("rrn"));
        assertEquals("654321", processing.get("stan"));

        assertEquals("https://example.org", payload.getLoyaltyQrPayload());
        assertEquals("base64-loyalty", payload.getLoyaltyQrCodeData());
        assertEquals("Coupon text", payload.getLoyaltyText());
    }

    @Test
    void mapPaymentStatus_returnsEmptyProcessingDataWhenAbsent() throws Exception {
        JsonNode response = objectMapper.readTree("{"
            + "\"paymentId\":\"p4\","
            + "\"status\":\"pending\""
            + "}");

        SbgPayStatusMapper.PaymentStatusPayload payload =
            SbgPayStatusMapper.mapPaymentStatus(response);

        assertTrue(payload.getProcessingData().isEmpty());
        assertNull(payload.getLoyaltyText());
    }
}

