package uz.sbgpay.set10.payment;

/**
 * Immutable runtime configuration of the SBG Pay plugin.
 */
final class SbgPayConfiguration {

    /** Base URL of processing API. */
    private final String baseUrl;
    /** Device token for authentication. */
    private final String deviceToken;
    /** Language used for API requests and UI labels. */
    private final String language;
    /** Currency code used in requests. */
    private final String currency;
    /** Payment time-to-live in seconds. */
    private final int ttlSeconds;
    /** Polling settings. */
    private final PollingSettings pollingSettings;
    /** Whether receipt lines should be sent in create request. */
    private final boolean sendReceipt;
    /** Loyalty message display duration in seconds. */
    private final int loyaltyDisplaySeconds;

    /**
     * Creates a new immutable configuration instance.
     *
     * @param baseUrlValue payment API base URL
     * @param deviceTokenValue token used by Device-Token header
     * @param languageValue API language
     * @param currencyValue ISO-like currency code
     * @param ttlSecondsValue payment TTL in seconds
     * @param pollingSettingsValue polling settings
     * @param sendReceiptValue whether receipt should be sent
     * @param loyaltyDisplaySecondsValue loyalty display duration in seconds
     */
    SbgPayConfiguration(final String baseUrlValue,
                        final String deviceTokenValue,
                        final String languageValue,
                        final String currencyValue,
                        final int ttlSecondsValue,
                        final PollingSettings pollingSettingsValue,
                        final boolean sendReceiptValue,
                        final int loyaltyDisplaySecondsValue) {
        this.baseUrl = baseUrlValue;
        this.deviceToken = deviceTokenValue;
        this.language = languageValue;
        this.currency = currencyValue;
        this.ttlSeconds = ttlSecondsValue;
        this.pollingSettings = pollingSettingsValue;
        this.sendReceipt = sendReceiptValue;
        this.loyaltyDisplaySeconds = loyaltyDisplaySecondsValue;
    }

    /**
     * Returns base URL.
     *
     * @return base URL
     */
    String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Returns device token.
     *
     * @return token or null
     */
    String getDeviceToken() {
        return deviceToken;
    }

    /**
     * Returns language.
     *
     * @return language
     */
    String getLanguage() {
        return language;
    }

    /**
     * Returns currency.
     *
     * @return currency
     */
    String getCurrency() {
        return currency;
    }

    /**
     * Returns TTL seconds.
     *
     * @return TTL seconds
     */
    int getTtlSeconds() {
        return ttlSeconds;
    }

    /**
     * Returns poll delay milliseconds.
     *
     * @return delay milliseconds
     */
    int getPollDelayMs() {
        return pollingSettings.getPollDelayMs();
    }

    /**
     * Returns poll timeout seconds.
     *
     * @return timeout seconds
     */
    int getPollTimeoutSeconds() {
        return pollingSettings.getPollTimeoutSeconds();
    }

    /**
     * Returns receipt send flag.
     *
     * @return true if receipt should be sent
     */
    boolean isSendReceipt() {
        return sendReceipt;
    }

    /**
     * Returns loyalty display duration.
     *
     * @return loyalty display duration in seconds
     */
    int getLoyaltyDisplaySeconds() {
        return loyaltyDisplaySeconds;
    }

    /**
     * Builds compact snapshot for debug logging.
     *
     * @return snapshot string
     */
    String snapshot() {
        return "baseUrl=" + raw(baseUrl)
            + ", lang=" + raw(language)
            + ", currency=" + raw(currency)
            + ", ttl=" + ttlSeconds
            + ", pollDelay=" + pollingSettings.getPollDelayMs()
            + ", pollTimeout=" + pollingSettings.getPollTimeoutSeconds()
            + ", sendReceipt=" + sendReceipt
            + ", loyaltyDisplaySeconds=" + loyaltyDisplaySeconds;
    }

    /**
     * Formats nullable value for logs.
     *
     * @param value raw value
     * @return formatted value
     */
    private String raw(final String value) {
        return value == null ? "<null>" : "'" + value + "'";
    }

    /**
     * Polling configuration values.
     */
    static final class PollingSettings {

        /** Polling interval in milliseconds. */
        private final int pollDelayMs;
        /** Polling timeout in seconds. */
        private final int pollTimeoutSeconds;

        /**
         * Creates polling settings.
         *
         * @param pollDelayMsValue delay in milliseconds
         * @param pollTimeoutSecondsValue timeout in seconds
         */
        PollingSettings(final int pollDelayMsValue,
                        final int pollTimeoutSecondsValue) {
            this.pollDelayMs = pollDelayMsValue;
            this.pollTimeoutSeconds = pollTimeoutSecondsValue;
        }

        /**
         * Returns poll delay.
         *
         * @return delay milliseconds
         */
        int getPollDelayMs() {
            return pollDelayMs;
        }

        /**
         * Returns poll timeout.
         *
         * @return timeout seconds
         */
        int getPollTimeoutSeconds() {
            return pollTimeoutSeconds;
        }
    }
}
