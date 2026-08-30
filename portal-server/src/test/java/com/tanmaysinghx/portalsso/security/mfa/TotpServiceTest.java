package com.tanmaysinghx.portalsso.security.mfa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TotpServiceTest {

    private TotpService totpService;

    @BeforeEach
    void setUp() {
        totpService = new TotpService();
    }

    @Test
    void generatesValidBase32Secret() {
        String secret = totpService.generateSecret();
        assertThat(secret).isNotBlank();
        assertThat(secret).matches("^[A-Z2-7]+$");

        byte[] decoded = TotpService.decodeBase32(secret);
        assertThat(decoded).hasSize(20);
    }

    @Test
    void generatesProvisioningUri() {
        String secret = "JBSWY3DPEHPK3PXP";
        String uri = totpService.generateProvisioningUri("user@example.com", secret, "Portal SSO");

        assertThat(uri).startsWith("otpauth://totp/Portal%20SSO:user%40example.com");
        assertThat(uri).contains("secret=" + secret);
        assertThat(uri).contains("issuer=Portal%20SSO");
        assertThat(uri).contains("digits=6");
        assertThat(uri).contains("period=30");
    }

    @Test
    void verifiesValidCodeAtCurrentStep() {
        String secret = totpService.generateSecret();
        long now = 1700000000L; // fixed timestamp

        String code = totpService.generateCode(secret, now);
        OptionalLong verified = totpService.verifyCode(secret, code, now, null);

        assertThat(verified).isPresent();
        assertThat(verified.getAsLong()).isEqualTo(now / 30);
    }

    @Test
    void verifiesCodeWithinToleranceWindow() {
        String secret = totpService.generateSecret();
        long now = 1700000000L;
        long timeStep = now / 30;

        // Code generated 30s in the past (step - 1)
        String pastCode = totpService.generateCode(secret, now - 30);
        OptionalLong pastVerified = totpService.verifyCode(secret, pastCode, now, null);
        assertThat(pastVerified).isPresent();
        assertThat(pastVerified.getAsLong()).isEqualTo(timeStep - 1);

        // Code generated 30s in the future (step + 1)
        String futureCode = totpService.generateCode(secret, now + 30);
        OptionalLong futureVerified = totpService.verifyCode(secret, futureCode, now, null);
        assertThat(futureVerified).isPresent();
        assertThat(futureVerified.getAsLong()).isEqualTo(timeStep + 1);
    }

    @Test
    void rejectsCodeOutsideWindow() {
        String secret = totpService.generateSecret();
        long now = 1700000000L;

        // 60s in the past (step - 2)
        String tooOldCode = totpService.generateCode(secret, now - 60);
        assertThat(totpService.verifyCode(secret, tooOldCode, now, null)).isEmpty();

        // 60s in the future (step + 2)
        String tooFarFutureCode = totpService.generateCode(secret, now + 60);
        assertThat(totpService.verifyCode(secret, tooFarFutureCode, now, null)).isEmpty();
    }

    @Test
    void preventsReplayOfAlreadyUsedTimeStep() {
        String secret = totpService.generateSecret();
        long now = 1700000000L;
        long currentStep = now / 30;

        String code = totpService.generateCode(secret, now);

        // First verification succeeds
        OptionalLong first = totpService.verifyCode(secret, code, now, null);
        assertThat(first).isPresent();
        assertThat(first.getAsLong()).isEqualTo(currentStep);

        // Replay attempt with lastUsedTimeStep = currentStep MUST be rejected
        OptionalLong replay = totpService.verifyCode(secret, code, now, currentStep);
        assertThat(replay).isEmpty();
    }

    @Test
    void rejectsInvalidOrMalformedCodes() {
        String secret = totpService.generateSecret();
        long now = 1700000000L;

        assertThat(totpService.verifyCode(secret, "000000", now, null)).isEmpty();
        assertThat(totpService.verifyCode(secret, "12345", now, null)).isEmpty();
        assertThat(totpService.verifyCode(secret, "abcdef", now, null)).isEmpty();
        assertThat(totpService.verifyCode(secret, "", now, null)).isEmpty();
        assertThat(totpService.verifyCode(secret, null, now, null)).isEmpty();
    }
}
