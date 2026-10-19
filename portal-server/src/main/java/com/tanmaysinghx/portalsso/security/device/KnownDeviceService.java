package com.tanmaysinghx.portalsso.security.device;

import com.tanmaysinghx.portalsso.security.device.entity.KnownDevice;
import com.tanmaysinghx.portalsso.security.device.repository.KnownDeviceRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Remembers which devices an account has signed in from, so a genuinely new one can be noticed. */
@Service
public class KnownDeviceService {

    private static final Logger log = LoggerFactory.getLogger(KnownDeviceService.class);

    private final KnownDeviceRepository repository;

    public KnownDeviceService(KnownDeviceRepository repository) {
        this.repository = repository;
    }

    /**
     * Records a sign-in and reports whether this device had been seen before.
     *
     * @return the outcome, with {@code firstSeen} false whenever the answer is uncertain — see
     *     below on why that direction.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Outcome recordSignIn(UUID userId, String userAgent, String ipAddress, String location) {
        DeviceSignature signature = DeviceSignature.parse(userAgent);
        String hash = signature.hash();
        String label = signature.label();

        Optional<KnownDevice> existing = repository.findByUserIdAndDeviceHash(userId, hash);
        if (existing.isPresent()) {
            KnownDevice device = existing.get();
            device.setLastSeenAt(Instant.now());
            device.setLastIpAddress(ipAddress);
            device.setLastCountryName(location);
            repository.save(device);
            return new Outcome(false, label);
        }

        KnownDevice device = new KnownDevice(userId, hash, label, Instant.now());
        device.setLastIpAddress(ipAddress);
        device.setLastCountryName(location);
        try {
            repository.saveAndFlush(device);
        } catch (DataIntegrityViolationException e) {
            // Two sign-ins from the same new device at once. The unique constraint means exactly one
            // insert wins, and the loser must report "not new" so only one alert is sent — reporting
            // it as new here is how a user ends up with two identical security emails.
            //
            // The flush above is what makes this catchable at all: without it the violation would
            // surface when the caller's transaction commits, far from the code that can interpret it.
            log.debug("Device {} for user {} was registered concurrently", hash, userId);
            return new Outcome(false, label);
        }
        return new Outcome(true, label);
    }

    /**
     * Forgets every device for an account, so each one is treated as new again.
     *
     * <p>Belongs with the administrative actions that assume an account may be compromised — an MFA
     * reset, say. Without this, a device an attacker signed in from stays silently trusted after the
     * account has been recovered, which is the one moment the alerts matter most.
     */
    @Transactional
    public void forgetDevicesFor(UUID userId) {
        repository.deleteByUserId(userId);
    }

    /**
     * @param firstSeen true when this account had never signed in from this device before.
     * @param deviceLabel human-readable signature, for the alert.
     */
    public record Outcome(boolean firstSeen, String deviceLabel) {}
}
