package com.tanmaysinghx.portalsso.mail.service;

import java.time.Instant;

/**
 * The "where and when" shown in a sign-in or new-device message.
 *
 * <p>An immutable snapshot taken on the request thread, not a reference to anything. The
 * notification that reads it runs asynchronously, by which point the servlet request is gone and
 * any JPA entity would be detached — so everything the template needs is copied out up front.
 *
 * @param location resolved country and city, already formatted. Null when GeoIP is not configured,
 *     which is the default; the templates omit the line rather than printing "Unknown".
 */
public record SignInDetails(
        String deviceLabel, String ipAddress, String location, Instant occurredAt) {

    public static SignInDetails of(String deviceLabel, String ipAddress, String location) {
        return new SignInDetails(deviceLabel, ipAddress, location, Instant.now());
    }

    public boolean hasLocation() {
        return location != null && !location.isBlank();
    }
}
