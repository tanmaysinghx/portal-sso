package com.tanmaysinghx.portalsso.user.web.dto;

import com.tanmaysinghx.portalsso.security.device.entity.KnownDevice;
import java.time.Instant;
import java.util.UUID;

/**
 * A browser and operating system the account has signed in from.
 *
 * @param current whether it matches the browser making this request — the user's "this device".
 */
public record KnownDeviceResponse(
        UUID id,
        String label,
        String lastIpAddress,
        String lastLocation,
        Instant firstSeenAt,
        Instant lastSeenAt,
        boolean current) {

    public static KnownDeviceResponse from(KnownDevice device, boolean current) {
        return new KnownDeviceResponse(
                device.getId(),
                device.getDeviceLabel(),
                device.getLastIpAddress(),
                device.getLastCountryName(),
                device.getCreatedAt(),
                device.getLastSeenAt(),
                current);
    }
}
