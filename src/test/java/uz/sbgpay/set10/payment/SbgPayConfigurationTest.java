package uz.sbgpay.set10.payment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SbgPayConfigurationTest {

    @Test
    void snapshot_containsCoreValues() {
        SbgPayConfiguration configuration = new SbgPayConfiguration(
            "https://sbg.amasia.io/pos",
            "device-token",
            "ru",
            "UZS",
            120,
            new SbgPayConfiguration.PollingSettings(500, 120),
            true
        );

        String expected = "baseUrl='https://sbg.amasia.io/pos', lang='ru', "
            + "currency='UZS', ttl=120, pollDelay=500, pollTimeout=120, "
            + "sendReceipt=true";
        assertEquals(expected, configuration.snapshot());
    }
}
