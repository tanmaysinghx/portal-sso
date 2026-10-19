package com.tanmaysinghx.portalsso.mail.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Transport-level knobs that an administrator has no reason to set per relay.
 *
 * <p>Host, port, credentials and TLS mode live in {@code smtp_settings} because they differ per
 * deployment and change while the server runs. Timeouts are different: they exist so a wedged
 * relay cannot pin a request thread indefinitely, and the right value depends on the network
 * rather than on who is administering the console. They stay properties, with defaults that are
 * deliberately short — a test send that hangs for a minute is indistinguishable from a broken UI.
 *
 * @param connectionTimeout how long to wait for the TCP connection and TLS handshake.
 * @param readTimeout how long to wait for each reply from the relay.
 * @param writeTimeout how long to wait for the message body to be accepted.
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(
        Duration connectionTimeout,
        Duration readTimeout,
        Duration writeTimeout) {

    public MailProperties {
        connectionTimeout = connectionTimeout != null ? connectionTimeout : Duration.ofSeconds(10);
        readTimeout = readTimeout != null ? readTimeout : Duration.ofSeconds(10);
        writeTimeout = writeTimeout != null ? writeTimeout : Duration.ofSeconds(10);
    }
}
