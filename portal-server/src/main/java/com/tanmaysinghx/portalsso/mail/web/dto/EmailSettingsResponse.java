package com.tanmaysinghx.portalsso.mail.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * Email configuration as the console sees it.
 *
 * @param deliveryReady whether the SMTP relay could actually send right now. The console needs this
 *     alongside the switches: a notification showing as "On" while no relay is configured is a lie,
 *     and that combination is exactly what someone who turned these on first would be looking at.
 * @param notifications one row per message type, so the console renders the list from the server's
 *     own enum rather than keeping a parallel copy that drifts.
 */
public record EmailSettingsResponse(
        String productName,
        String logoUrl,
        String accentColor,
        String supportUrl,
        String footerNote,
        boolean welcomeEnabled,
        boolean newDeviceEnabled,
        boolean signInNoticeEnabled,
        boolean magicLinkEnabled,
        boolean magicLinkRegistrationEnabled,
        int magicLinkTtlMinutes,
        boolean deliveryReady,
        String portalUrl,
        String updatedBy,
        Instant updatedAt,
        List<NotificationStatus> notifications) {

    /**
     * @param status "ON", "OFF", or "NO_RELAY" — on, but nothing can be delivered. Three states
     *     rather than a boolean because "configured on but silently undeliverable" is the state an
     *     operator most needs to see, and a checkbox cannot show it.
     */
    public record NotificationStatus(
            String type, String label, String description, boolean enabled, String status) {}
}
