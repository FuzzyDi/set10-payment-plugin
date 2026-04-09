package uz.sbgpay.set10.payment;

import org.junit.jupiter.api.Test;
import ru.crystals.pos.api.plugin.RefundPreparationPlugin;
import ru.crystals.pos.api.plugin.TransactionalRefundPlugin;

import java.math.BigDecimal;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SbgPayPaymentPluginTest {

    @Test
    void getQrData_prefersQrPayload() throws Exception {
        Object plugin = newPlugin();
        Object status = newStatus("payload-data", "fallback-data");

        String qr = (String) getQrDataMethod().invoke(plugin, status);

        assertEquals("payload-data", qr);
    }

    @Test
    void getQrData_fallsBackToQrCodeData() throws Exception {
        Object plugin = newPlugin();
        Object status = newStatus(null, "fallback-data");

        String qr = (String) getQrDataMethod().invoke(plugin, status);

        assertEquals("fallback-data", qr);
    }

    @Test
    void getQrData_returnsNullWithoutQrFields() throws Exception {
        Object plugin = newPlugin();
        Object status = newStatus("", "");

        String qr = (String) getQrDataMethod().invoke(plugin, status);

        assertNull(qr);
    }

    @Test
    void isSuccessStatus_supportsCompletedAndPaid() throws Exception {
        Object plugin = newPlugin();
        Method isSuccessStatus = method("isSuccessStatus", String.class);

        assertTrue((Boolean) isSuccessStatus.invoke(plugin, "completed"));
        assertTrue((Boolean) isSuccessStatus.invoke(plugin, "paid"));
        assertTrue((Boolean) isSuccessStatus.invoke(plugin, "COMPLETED"));
        assertFalse((Boolean) isSuccessStatus.invoke(plugin, "pending"));
    }

    @Test
    void isFailedStatus_supportsTerminalErrorStates() throws Exception {
        Object plugin = newPlugin();
        Method isFailedStatus = method("isFailedStatus", String.class);

        assertTrue((Boolean) isFailedStatus.invoke(plugin, "failed"));
        assertTrue((Boolean) isFailedStatus.invoke(plugin, "declined"));
        assertTrue((Boolean) isFailedStatus.invoke(plugin, "cancelled"));
        assertTrue((Boolean) isFailedStatus.invoke(plugin, "canceled"));
        assertTrue((Boolean) isFailedStatus.invoke(plugin, "expired"));
        assertFalse((Boolean) isFailedStatus.invoke(plugin, "processing"));
    }

    @Test
    void calculateStatusRequestTimeoutMs_isBoundedByDefaultHttpTimeout() throws Exception {
        Object plugin = newPlugin();
        Method calculate = method("calculateStatusRequestTimeoutMs", long.class);

        assertEquals(30000, ((Integer) calculate.invoke(plugin, 60000L)).intValue());
        assertEquals(5000, ((Integer) calculate.invoke(plugin, 5000L)).intValue());
        assertEquals(1, ((Integer) calculate.invoke(plugin, 0L)).intValue());
    }

    @Test
    void parseIntegerConfigValue_supportsIntegerAndDecimalForms() throws Exception {
        Object plugin = newPlugin();
        Method parse = method("parseIntegerConfigValue", String.class);

        assertEquals(60, ((Integer) parse.invoke(plugin, "60")).intValue());
        assertEquals(60, ((Integer) parse.invoke(plugin, "60.0")).intValue());
        assertEquals(60, ((Integer) parse.invoke(plugin, "60,0")).intValue());
        assertEquals(2000, ((Integer) parse.invoke(plugin, "2 000")).intValue());
    }

    @Test
    void parseIntegerConfigValue_returnsNullForInvalidValue() throws Exception {
        Object plugin = newPlugin();
        Method parse = method("parseIntegerConfigValue", String.class);

        assertNull(parse.invoke(plugin, "60.5"));
        assertNull(parse.invoke(plugin, "abc"));
        assertNull(parse.invoke(plugin, ""));
        assertNull(parse.invoke(plugin, (Object) null));
    }

    @Test
    void parseBooleanConfigValue_supportsCommonForms() throws Exception {
        Object plugin = newPlugin();
        Method parse = method("parseBooleanConfigValue", String.class);

        assertEquals(Integer.valueOf(1), parse.invoke(plugin, "true"));
        assertEquals(Integer.valueOf(1), parse.invoke(plugin, "1"));
        assertEquals(Integer.valueOf(1), parse.invoke(plugin, "on"));
        assertEquals(Integer.valueOf(0), parse.invoke(plugin, "false"));
        assertEquals(Integer.valueOf(0), parse.invoke(plugin, "0"));
        assertEquals(Integer.valueOf(0), parse.invoke(plugin, "off"));
    }

    @Test
    void parseBooleanConfigValue_returnsNullForInvalidValue() throws Exception {
        Object plugin = newPlugin();
        Method parse = method("parseBooleanConfigValue", String.class);

        assertNull(parse.invoke(plugin, "maybe"));
        assertNull(parse.invoke(plugin, ""));
        assertNull(parse.invoke(plugin, (Object) null));
    }

    @Test
    void isAlreadyCompletedConflict_accepts409Completed() throws Exception {
        Object plugin = newPlugin();
        Class<?> exClass = Class.forName("uz.sbgpay.set10.payment.SbgPayPaymentPlugin$HttpStatusException");
        Constructor<?> ctor = exClass.getDeclaredConstructor(int.class, String.class, String.class);
        ctor.setAccessible(true);
        Object ex = ctor.newInstance(409, "invalid_status", "Cannot complete payment with status 'Completed'");

        Method method = method("isAlreadyCompletedConflict", exClass);
        assertTrue((Boolean) method.invoke(plugin, ex));
    }

    @Test
    void isAlreadyReversedConflict_accepts409Reversed() throws Exception {
        Object plugin = newPlugin();
        Class<?> exClass = Class.forName("uz.sbgpay.set10.payment.SbgPayPaymentPlugin$HttpStatusException");
        Constructor<?> ctor = exClass.getDeclaredConstructor(int.class, String.class, String.class);
        ctor.setAccessible(true);
        Object ex = ctor.newInstance(409, "invalid_status", "Cannot reverse payment with status 'Reversed'");

        Method method = method("isAlreadyReversedConflict", exClass);
        assertTrue((Boolean) method.invoke(plugin, ex));
    }

    @Test
    void isCancelConflictForCompleted_accepts409Completed() throws Exception {
        Object plugin = newPlugin();
        Class<?> exClass = Class.forName("uz.sbgpay.set10.payment.SbgPayPaymentPlugin$HttpStatusException");
        Constructor<?> ctor = exClass.getDeclaredConstructor(int.class, String.class, String.class);
        ctor.setAccessible(true);
        Object ex = ctor.newInstance(409, "invalid_status", "Cannot cancel payment with status 'Completed'");

        Method method = method("isCancelConflictForCompleted", exClass);
        assertTrue((Boolean) method.invoke(plugin, ex));
    }

    @Test
    void isCancelConflictForCompleted_rejectsNonCompletedConflicts() throws Exception {
        Object plugin = newPlugin();
        Class<?> exClass = Class.forName("uz.sbgpay.set10.payment.SbgPayPaymentPlugin$HttpStatusException");
        Constructor<?> ctor = exClass.getDeclaredConstructor(int.class, String.class, String.class);
        ctor.setAccessible(true);
        Object ex = ctor.newInstance(409, "invalid_status", "Cannot cancel payment with status 'Pending'");

        Method method = method("isCancelConflictForCompleted", exClass);
        assertFalse((Boolean) method.invoke(plugin, ex));
    }

    @Test
    void statusHelpers_supportCancelledAndReversed() throws Exception {
        Object plugin = newPlugin();
        Method isCancelledStatus = method("isCancelledStatus", String.class);
        Method isReversedStatus = method("isReversedStatus", String.class);

        assertTrue((Boolean) isCancelledStatus.invoke(plugin, "cancelled"));
        assertTrue((Boolean) isCancelledStatus.invoke(plugin, "canceled"));
        assertFalse((Boolean) isCancelledStatus.invoke(plugin, "completed"));

        assertTrue((Boolean) isReversedStatus.invoke(plugin, "reversed"));
        assertFalse((Boolean) isReversedStatus.invoke(plugin, "refunded"));
    }

    @Test
    void isCommunicationException_detectsUnknownHost() throws Exception {
        Object plugin = newPlugin();
        Method method = method("isCommunicationException", Throwable.class);

        assertTrue((Boolean) method.invoke(plugin, new UnknownHostException("sbg.amasia.io")));
        assertFalse((Boolean) method.invoke(plugin, new SocketTimeoutException("Read timed out")));
        assertFalse((Boolean) method.invoke(plugin, new RuntimeException("boom")));
    }

    @Test
    void resolveErrorMessage_returnsGenericCommunicationMessage() throws Exception {
        Object plugin = newPlugin();
        Method method = method("resolveErrorMessage", Exception.class, String.class, String.class);

        String message = (String) method.invoke(
            plugin,
            new UnknownHostException("sbg.amasia.io"),
            "error.load.methods",
            "Failed to load payment methods: "
        );
        assertEquals("Нет связи с процессингом. Проверьте сеть и повторите операцию.", message);
    }

    @Test
    void resolveErrorMessage_returnsTimeoutMessageForSocketTimeout() throws Exception {
        Object plugin = newPlugin();
        Method method = method("resolveErrorMessage", Exception.class, String.class, String.class);

        String message = (String) method.invoke(
            plugin,
            new SocketTimeoutException("Read timed out"),
            "error.load.methods",
            "Failed to load payment methods: "
        );
        assertEquals("Превышено время ожидания ответа от процессинга. Повторите операцию.", message);
    }

    @Test
    void resolveCompletedPaymentId_prefersStatusPaymentId() throws Exception {
        Object plugin = newPlugin();
        Field currentPaymentId = SbgPayPaymentPlugin.class.getDeclaredField("currentPaymentId");
        currentPaymentId.setAccessible(true);
        currentPaymentId.set(plugin, "create-id");

        Class<?> statusClass = statusClass();
        Constructor<?> ctor = statusClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object status = ctor.newInstance();
        Field statusPaymentId = statusClass.getDeclaredField("paymentId");
        statusPaymentId.setAccessible(true);
        statusPaymentId.set(status, "status-id");

        Method method = method("resolveCompletedPaymentId", statusClass);
        assertEquals("status-id", method.invoke(plugin, status));
    }

    @Test
    void extractPaymentIdFromData_readsFallbackKey() throws Exception {
        Object plugin = newPlugin();
        Method method = method("extractPaymentIdFromData", Map.class);

        Map<String, String> data = new HashMap<>();
        data.put("paymentId", "fallback-id");

        assertEquals("fallback-id", method.invoke(plugin, data));
    }

    @Test
    void isFullRefundAmount_requiresExactAmount() throws Exception {
        Object plugin = newPlugin();
        Method method = method("isFullRefundAmount", BigDecimal.class, BigDecimal.class);

        assertTrue((Boolean) method.invoke(plugin, new BigDecimal("100.00"), new BigDecimal("100")));
        assertFalse((Boolean) method.invoke(plugin, new BigDecimal("99.99"), new BigDecimal("100.00")));
        assertFalse((Boolean) method.invoke(plugin, new BigDecimal("100.00"), null));
    }

    @Test
    void plugin_implementsTransactionalAndPreparationRefundContracts() {
        assertTrue(RefundPreparationPlugin.class.isAssignableFrom(SbgPayPaymentPlugin.class));
        assertTrue(TransactionalRefundPlugin.class.isAssignableFrom(SbgPayPaymentPlugin.class));
    }

    @Test
    void acquireCancelOperation_joinsExistingInFlightOperation() throws Exception {
        Object plugin = newPlugin();
        String paymentId = "pay-" + UUID.randomUUID();

        Method acquire = method("acquireCancelOperation", String.class);
        Object firstHandle = acquire.invoke(plugin, paymentId);
        Object secondHandle = acquire.invoke(plugin, paymentId);

        Field ownerField = firstHandle.getClass().getDeclaredField("owner");
        ownerField.setAccessible(true);
        assertTrue((Boolean) ownerField.get(firstHandle));
        assertFalse((Boolean) ownerField.get(secondHandle));

        Field operationField = firstHandle.getClass().getDeclaredField("operation");
        operationField.setAccessible(true);
        Object firstOperation = operationField.get(firstHandle);
        Object secondOperation = operationField.get(secondHandle);
        assertTrue(firstOperation == secondOperation);
    }

    @Test
    void completeCancelOperation_cachesSuccessfulResult() throws Exception {
        Object plugin = newPlugin();
        String paymentId = "pay-" + UUID.randomUUID();

        Method acquire = method("acquireCancelOperation", String.class);
        Object handle = acquire.invoke(plugin, paymentId);

        Field operationField = handle.getClass().getDeclaredField("operation");
        operationField.setAccessible(true);
        Object operation = operationField.get(handle);

        Class<?> resultClass = Class.forName(
            "uz.sbgpay.set10.payment.SbgPayPaymentPlugin$CancelOperationResult");
        Constructor<?> resultCtor = resultClass.getDeclaredConstructor(
            boolean.class,
            String.class
        );
        resultCtor.setAccessible(true);
        Object successResult = resultCtor.newInstance(true, "ok");

        Method complete = method(
            "completeCancelOperation",
            String.class,
            Class.forName(
                "uz.sbgpay.set10.payment.SbgPayPaymentPlugin$CancelOperationInFlight"),
            resultClass
        );
        complete.invoke(plugin, paymentId, operation, successResult);

        Method fresh = method("getFreshCancelResult", String.class);
        Object cached = fresh.invoke(plugin, paymentId);
        assertTrue(cached != null);

        Field successField = resultClass.getDeclaredField("success");
        successField.setAccessible(true);
        assertTrue((Boolean) successField.get(cached));
    }

    private Object newPlugin() throws Exception {
        return SbgPayPaymentPlugin.class.getDeclaredConstructor().newInstance();
    }

    private Object newStatus(String qrPayloadValue, String qrCodeDataValue) throws Exception {
        Class<?> statusClass = statusClass();
        Constructor<?> ctor = statusClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object status = ctor.newInstance();

        Field qrPayload = statusClass.getDeclaredField("qrPayload");
        qrPayload.setAccessible(true);
        qrPayload.set(status, qrPayloadValue);

        Field qrCodeData = statusClass.getDeclaredField("qrCodeData");
        qrCodeData.setAccessible(true);
        qrCodeData.set(status, qrCodeDataValue);

        return status;
    }

    private Method getQrDataMethod() throws Exception {
        return method("getQrData", statusClass());
    }

    private Method method(String name, Class<?>... parameterTypes) throws Exception {
        Method method = SbgPayPaymentPlugin.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private Class<?> statusClass() throws ClassNotFoundException {
        return Class.forName("uz.sbgpay.set10.payment.SbgPayPaymentPlugin$PaymentStatus");
    }
}
