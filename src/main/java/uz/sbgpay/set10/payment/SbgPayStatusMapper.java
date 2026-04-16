package uz.sbgpay.set10.payment;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

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
        final JsonNode loyalty = objectNode(response, "loyalty");
        return PaymentStatusPayload.builder()
            .paymentId(text(response, "paymentId"))
            .paymentCode(text(response, "paymentCode"))
            .status(text(response, "status"))
            .qrPayload(text(response, "qrPayload"))
            .qrCodeData(text(response, "qrCodeData"))
            .processingData(toStringMap(objectNode(response, "processingData")))
            .loyaltyQrPayload(text(loyalty, "qrPayload"))
            .loyaltyQrCodeData(text(loyalty, "qrCodeData"))
            .loyaltyText(firstNonEmpty(
                text(loyalty, "text"),
                text(loyalty, "message"),
                text(loyalty, "description")
            ))
            .errorMessage(firstNonEmpty(
                text(response, "errorMessage"),
                text(response, "error"),
                text(response, "message")
            ))
            .build();
    }

    /**
     * Gets object node field value from JSON node.
     *
     * @param node source node
     * @param key field name
     * @return object node or null
     */
    private static JsonNode objectNode(final JsonNode node,
                                       final String key) {
        if (node == null || key == null || !node.has(key)
            || node.get(key).isNull()) {
            return null;
        }
        final JsonNode value = node.get(key);
        return value != null && value.isObject() ? value : null;
    }

    /**
     * Converts object node to a string map preserving source field order.
     *
     * @param node source object node
     * @return immutable map with scalar/stringified values
     */
    private static Map<String, String> toStringMap(final JsonNode node) {
        if (node == null || !node.isObject()) {
            return Collections.emptyMap();
        }

        final Map<String, String> result = new LinkedHashMap<>();
        final Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> field = fields.next();
            final JsonNode value = field.getValue();
            if (field.getKey() == null || value == null || value.isNull()) {
                continue;
            }

            if (value.isValueNode()) {
                result.put(field.getKey(), value.asText());
            } else {
                result.put(field.getKey(), value.toString());
            }
        }
        if (result.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(result);
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
        /** Payment processing details for receipt/slip. */
        private final Map<String, String> processingData;
        /** Loyalty QR payload value (text/url). */
        private final String loyaltyQrPayload;
        /** Loyalty QR bitmap value (base64). */
        private final String loyaltyQrCodeData;
        /** Loyalty text for customer display. */
        private final String loyaltyText;
        /** Optional error details. */
        private final String errorMessage;

        /**
         * Creates payload from builder.
         *
         * @param builderValue source builder
         */
        PaymentStatusPayload(final Builder builderValue) {
            this.paymentId = builderValue.paymentId;
            this.paymentCode = builderValue.paymentCode;
            this.status = builderValue.status;
            this.qrPayload = builderValue.qrPayload;
            this.qrCodeData = builderValue.qrCodeData;
            this.processingData = builderValue.processingData;
            this.loyaltyQrPayload = builderValue.loyaltyQrPayload;
            this.loyaltyQrCodeData = builderValue.loyaltyQrCodeData;
            this.loyaltyText = builderValue.loyaltyText;
            this.errorMessage = builderValue.errorMessage;
        }

        /**
         * Creates builder instance.
         *
         * @return builder
         */
        static Builder builder() {
            return new Builder();
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
         * Returns payment processing details.
         *
         * @return immutable processing data map
         */
        Map<String, String> getProcessingData() {
            return processingData;
        }

        /**
         * Returns loyalty qr payload value.
         *
         * @return loyalty qr payload
         */
        String getLoyaltyQrPayload() {
            return loyaltyQrPayload;
        }

        /**
         * Returns loyalty qr code data.
         *
         * @return loyalty qr code data
         */
        String getLoyaltyQrCodeData() {
            return loyaltyQrCodeData;
        }

        /**
         * Returns loyalty text.
         *
         * @return loyalty text
         */
        String getLoyaltyText() {
            return loyaltyText;
        }

        /**
         * Returns error message.
         *
         * @return error message
         */
        String getErrorMessage() {
            return errorMessage;
        }

        /**
         * Builder for immutable payload.
         */
        static final class Builder {

            /** Payment identifier. */
            private String paymentId;
            /** Human readable payment code. */
            private String paymentCode;
            /** Payment status value. */
            private String status;
            /** QR payload for dynamic generation. */
            private String qrPayload;
            /** QR base64 bitmap value. */
            private String qrCodeData;
            /** Payment processing details for receipt/slip. */
            private Map<String, String> processingData =
                Collections.emptyMap();
            /** Loyalty QR payload value (text/url). */
            private String loyaltyQrPayload;
            /** Loyalty QR bitmap value (base64). */
            private String loyaltyQrCodeData;
            /** Loyalty text for customer display. */
            private String loyaltyText;
            /** Optional error details. */
            private String errorMessage;

            /**
             * Builder constructor.
             */
            Builder() {
            }

            /**
             * Sets payment identifier.
             *
             * @param paymentIdValue payment id
             * @return this builder
             */
            Builder paymentId(final String paymentIdValue) {
                this.paymentId = paymentIdValue;
                return this;
            }

            /**
             * Sets payment code.
             *
             * @param paymentCodeValue payment code
             * @return this builder
             */
            Builder paymentCode(final String paymentCodeValue) {
                this.paymentCode = paymentCodeValue;
                return this;
            }

            /**
             * Sets status.
             *
             * @param statusValue status
             * @return this builder
             */
            Builder status(final String statusValue) {
                this.status = statusValue;
                return this;
            }

            /**
             * Sets QR payload.
             *
             * @param qrPayloadValue qr payload
             * @return this builder
             */
            Builder qrPayload(final String qrPayloadValue) {
                this.qrPayload = qrPayloadValue;
                return this;
            }

            /**
             * Sets QR code data.
             *
             * @param qrCodeDataValue qr code data
             * @return this builder
             */
            Builder qrCodeData(final String qrCodeDataValue) {
                this.qrCodeData = qrCodeDataValue;
                return this;
            }

            /**
             * Sets processing data.
             *
             * @param processingDataValue processing data map
             * @return this builder
             */
            Builder processingData(final Map<String, String>
                                       processingDataValue) {
                if (processingDataValue == null
                    || processingDataValue.isEmpty()) {
                    this.processingData = Collections.emptyMap();
                } else {
                    this.processingData = processingDataValue;
                }
                return this;
            }

            /**
             * Sets loyalty qr payload.
             *
             * @param loyaltyQrPayloadValue loyalty qr payload
             * @return this builder
             */
            Builder loyaltyQrPayload(
                final String loyaltyQrPayloadValue) {
                this.loyaltyQrPayload = loyaltyQrPayloadValue;
                return this;
            }

            /**
             * Sets loyalty qr code data.
             *
             * @param loyaltyQrCodeDataValue loyalty qr code data
             * @return this builder
             */
            Builder loyaltyQrCodeData(
                final String loyaltyQrCodeDataValue) {
                this.loyaltyQrCodeData = loyaltyQrCodeDataValue;
                return this;
            }

            /**
             * Sets loyalty text.
             *
             * @param loyaltyTextValue loyalty text
             * @return this builder
             */
            Builder loyaltyText(final String loyaltyTextValue) {
                this.loyaltyText = loyaltyTextValue;
                return this;
            }

            /**
             * Sets error message.
             *
             * @param errorMessageValue error message
             * @return this builder
             */
            Builder errorMessage(final String errorMessageValue) {
                this.errorMessage = errorMessageValue;
                return this;
            }

            /**
             * Builds immutable payload.
             *
             * @return mapped payload
             */
            PaymentStatusPayload build() {
                return new PaymentStatusPayload(this);
            }
        }
    }
}
