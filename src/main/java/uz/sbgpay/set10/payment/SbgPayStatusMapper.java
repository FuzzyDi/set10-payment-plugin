package uz.sbgpay.set10.payment;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Mapper utilities for payment status API payloads.
 */
final class SbgPayStatusMapper {

    /**
     * Utility class.
     */
    private SbgPayStatusMapper() {
    }

    /**
     * Maps status response JSON to immutable payload.
     *
     * @param response status response JSON node
     * @return mapped payload
     */
    static PaymentStatusPayload mapPaymentStatus(
        final JsonNode response) {
        return new PaymentStatusPayload(
            text(response, "paymentId"),
            text(response, "paymentCode"),
            text(response, "status"),
            text(response, "qrPayload"),
            text(response, "qrCodeData"),
            firstNonEmpty(
                text(response, "errorMessage"),
                text(response, "error"),
                text(response, "message")
            )
        );
    }

    /**
     * Gets text field value from JSON node.
     *
     * @param node source node
     * @param key field name
     * @return text value or null
     */
    private static String text(final JsonNode node, final String key) {
        if (node == null || key == null || !node.has(key)
            || node.get(key).isNull()) {
            return null;
        }
        return node.get(key).asText();
    }

    /**
     * Returns first non-empty value.
     *
     * @param first first candidate
     * @param second second candidate
     * @param third third candidate
     * @return first non-empty value or null
     */
    private static String firstNonEmpty(final String first,
                                        final String second,
                                        final String third) {
        if (hasText(first)) {
            return first;
        }
        if (hasText(second)) {
            return second;
        }
        if (hasText(third)) {
            return third;
        }
        return null;
    }

    /**
     * Checks whether a string has non-whitespace text.
     *
     * @param value source value
     * @return true if value is not blank
     */
    private static boolean hasText(final String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * Immutable mapped payload for payment status.
     */
    static final class PaymentStatusPayload {

        /** Payment identifier. */
        private final String paymentId;
        /** Human readable payment code. */
        private final String paymentCode;
        /** Payment status value. */
        private final String status;
        /** QR payload for dynamic generation. */
        private final String qrPayload;
        /** QR base64 bitmap value. */
        private final String qrCodeData;
        /** Optional error details. */
        private final String errorMessage;

        /**
         * Creates payload.
         *
         * @param paymentIdValue payment identifier
         * @param paymentCodeValue payment code
         * @param statusValue status value
         * @param qrPayloadValue qr payload text
         * @param qrCodeDataValue qr bitmap data
         * @param errorMessageValue error details
         */
        PaymentStatusPayload(final String paymentIdValue,
                             final String paymentCodeValue,
                             final String statusValue,
                             final String qrPayloadValue,
                             final String qrCodeDataValue,
                             final String errorMessageValue) {
            this.paymentId = paymentIdValue;
            this.paymentCode = paymentCodeValue;
            this.status = statusValue;
            this.qrPayload = qrPayloadValue;
            this.qrCodeData = qrCodeDataValue;
            this.errorMessage = errorMessageValue;
        }

        /**
         * Returns payment identifier.
         *
         * @return payment id
         */
        String getPaymentId() {
            return paymentId;
        }

        /**
         * Returns payment code.
         *
         * @return payment code
         */
        String getPaymentCode() {
            return paymentCode;
        }

        /**
         * Returns status.
         *
         * @return status
         */
        String getStatus() {
            return status;
        }

        /**
         * Returns qr payload.
         *
         * @return qr payload
         */
        String getQrPayload() {
            return qrPayload;
        }

        /**
         * Returns qr code data.
         *
         * @return qr code data
         */
        String getQrCodeData() {
            return qrCodeData;
        }

        /**
         * Returns error message.
         *
         * @return error message
         */
        String getErrorMessage() {
            return errorMessage;
        }
    }
}
