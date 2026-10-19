package com.tanmaysinghx.portalsso.security.device;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The parsing behind "is this a new device?". The property that actually matters is the one in
 * {@link #aBrowserVersionBumpIsNotANewDevice()}: everything else is detail, but that one decides
 * whether the alert is useful or noise.
 */
class DeviceSignatureTest {

    private static final String CHROME_MAC_140 =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/140.0.0.0 Safari/537.36";
    private static final String CHROME_MAC_141 =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/141.0.0.0 Safari/537.36";
    private static final String SAFARI_IPHONE =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) "
                    + "Version/18.1 Mobile/15E148 Safari/604.1";
    private static final String FIREFOX_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0";
    private static final String EDGE_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0";

    /**
     * The whole reason versions are excluded from the signature. Chrome ships a major version
     * roughly every four weeks; if this failed, every user would get a "new device" security alert
     * monthly for the laptop on their desk, and would learn to ignore all of them.
     */
    @Test
    void aBrowserVersionBumpIsNotANewDevice() {
        assertThat(DeviceSignature.parse(CHROME_MAC_140).hash())
                .isEqualTo(DeviceSignature.parse(CHROME_MAC_141).hash());
    }

    @Test
    void aDifferentBrowserOnTheSameMachineIsANewDevice() {
        assertThat(DeviceSignature.parse(FIREFOX_WINDOWS).hash())
                .isNotEqualTo(DeviceSignature.parse(EDGE_WINDOWS).hash());
    }

    @Test
    void aDifferentOperatingSystemIsANewDevice() {
        assertThat(DeviceSignature.parse(CHROME_MAC_140).hash())
                .isNotEqualTo(DeviceSignature.parse(SAFARI_IPHONE).hash());
    }

    @Test
    void chromeIsNotMistakenForSafari() {
        // Chrome's UA string contains "Safari", and Edge's contains both "Chrome" and "Safari", so
        // the order of the checks is load-bearing rather than cosmetic.
        assertThat(DeviceSignature.parse(CHROME_MAC_140).browser()).isEqualTo("Chrome");
        assertThat(DeviceSignature.parse(EDGE_WINDOWS).browser()).isEqualTo("Microsoft Edge");
        assertThat(DeviceSignature.parse(SAFARI_IPHONE).browser()).isEqualTo("Safari");
    }

    @Test
    void anIphoneIsNotReportedAsMacOs() {
        // Mobile Safari reports "like Mac OS X", so the iPhone check has to come first.
        DeviceSignature signature = DeviceSignature.parse(SAFARI_IPHONE);
        assertThat(signature.os()).isEqualTo("iOS");
        assertThat(signature.deviceType()).isEqualTo("Mobile");
    }

    @Test
    void theLabelReadsAsSomethingARecipientWouldRecognise() {
        assertThat(DeviceSignature.parse(CHROME_MAC_140).label()).isEqualTo("Chrome on macOS");
        assertThat(DeviceSignature.parse(SAFARI_IPHONE).label()).isEqualTo("Safari on iOS (Mobile)");
    }

    @Test
    void aMissingUserAgentIsHandledRatherThanThrowing() {
        // A sign-in with no User-Agent header is unusual but entirely possible, and it must not
        // break the sign-in it is attached to.
        for (String ua : new String[] {null, "", "   "}) {
            DeviceSignature signature = DeviceSignature.parse(ua);
            assertThat(signature.hash()).isNotBlank();
            assertThat(signature.label()).isEqualTo("Unknown device");
        }
    }

    @Test
    void theHashIsStableAcrossCalls() {
        // It is a database key, so an unstable hash would mean every sign-in looks like a new device.
        assertThat(DeviceSignature.parse(CHROME_MAC_140).hash())
                .isEqualTo(DeviceSignature.parse(CHROME_MAC_140).hash())
                .hasSize(64);
    }
}
