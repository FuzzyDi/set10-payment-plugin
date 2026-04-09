package uz.sbgpay.set10.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}

