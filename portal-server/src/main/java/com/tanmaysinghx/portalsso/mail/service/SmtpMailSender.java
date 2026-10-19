package com.tanmaysinghx.portalsso.mail.service;

import com.tanmaysinghx.portalsso.mail.entity.SmtpTransportSecurity;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.Properties;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * The only place in the application that speaks SMTP.
 *
 * <p>It knows nothing about where configuration is stored or whether mail is switched on — it is
 * handed a complete {@link SmtpConnection} and sends. That is what lets the same transport serve
 * both {@link MailService}, which sends through the saved configuration, and
 * {@link SmtpSettingsService}, which has to prove values an administrator has not saved yet. If
 * this class loaded the settings itself those two would have to depend on each other.
 *
 * <h2>Why a sender is built per message</h2>
 *
 * <p>A {@link JavaMailSenderImpl} could be cached, but its configuration lives in a table an
 * administrator edits at will, and a cache would then need invalidating from every path that writes
 * those rows — including the in-app database migration, which replaces them wholesale behind this
 * service's back. Constructing one opens no connection and allocates almost nothing; the socket is
 * established on send either way.
 */
@Component
public class SmtpMailSender {

    private final MailProperties mailProperties;

    public SmtpMailSender(MailProperties mailProperties) {
        this.mailProperties = mailProperties;
    }

    /**
     * @throws MessagingFailure when the message cannot be assembled — a malformed address, say.
     * @throws MailException when the relay cannot be reached, refuses the credentials, or rejects
     *     the message.
     */
    public void send(SmtpConnection connection, String to, String subject, String body) {
        JavaMailSenderImpl sender = buildSender(connection);
        MimeMessage message = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            helper.setFrom(fromAddress(connection));
            if (connection.replyToAddress() != null && !connection.replyToAddress().isBlank()) {
                helper.setReplyTo(connection.replyToAddress());
            }
        } catch (jakarta.mail.MessagingException e) {
            throw new MessagingFailure(e);
        }
        sender.send(message);
    }

    /**
     * A display name is optional, and an unencodable one must not become a send failure — falling
     * back to the bare address still delivers the mail.
     */
    private static InternetAddress fromAddress(SmtpConnection connection) throws AddressException {
        String name = connection.fromName();
        if (name == null || name.isBlank()) {
            return new InternetAddress(connection.fromAddress());
        }
        try {
            return new InternetAddress(connection.fromAddress(), name, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return new InternetAddress(connection.fromAddress());
        }
    }

    private JavaMailSenderImpl buildSender(SmtpConnection connection) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(connection.host());
        sender.setPort(connection.port());
        sender.setDefaultEncoding("UTF-8");
        if (connection.hasCredentials()) {
            sender.setUsername(connection.username());
            sender.setPassword(connection.password());
        }

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(connection.hasCredentials()));
        props.put("mail.smtp.connectiontimeout", millis(mailProperties.connectionTimeout()));
        props.put("mail.smtp.timeout", millis(mailProperties.readTimeout()));
        props.put("mail.smtp.writetimeout", millis(mailProperties.writeTimeout()));

        if (connection.transportSecurity() == SmtpTransportSecurity.STARTTLS) {
            props.put("mail.smtp.starttls.enable", "true");
            // Required, not merely offered. Without this a relay that stops advertising STARTTLS —
            // or something in the middle stripping it — silently downgrades the session to
            // plaintext, and the password goes out in the clear with nothing in the logs to say so.
            props.put("mail.smtp.starttls.required", "true");
        } else if (connection.transportSecurity() == SmtpTransportSecurity.SSL_TLS) {
            props.put("mail.smtp.ssl.enable", "true");
        }
        // Certificates are verified against the JDK trust store in both TLS modes. There is
        // deliberately no "trust any certificate" setting: it is the first thing an operator would
        // reach for when a handshake fails, and it quietly removes the protection that choosing
        // STARTTLS or SSL/TLS was supposed to provide. A relay behind a private CA belongs in the
        // JVM trust store.
        return sender;
    }

    private static String millis(Duration duration) {
        return String.valueOf(duration.toMillis());
    }

    /**
     * Wraps the checked exceptions thrown while assembling a message, so callers handle one
     * unchecked type alongside Spring's {@link MailException} rather than three.
     */
    public static class MessagingFailure extends RuntimeException {
        MessagingFailure(Throwable cause) {
            super(cause);
        }
    }
}
