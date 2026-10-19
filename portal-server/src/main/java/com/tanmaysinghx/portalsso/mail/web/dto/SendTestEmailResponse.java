package com.tanmaysinghx.portalsso.mail.web.dto;

/**
 * The outcome of a test send.
 *
 * <p>A relay refusing the message is an expected answer to "does this configuration work?", not a
 * server fault, so a failure comes back as {@code 200} with {@code success=false} and the reason —
 * the same shape as the database connection test. Reserving non-2xx for genuine faults keeps the
 * console from having to read failures out of two different places.
 *
 * @param detail the underlying SMTP or network error, already reduced to something an operator can
 *     act on. Never contains the relay password.
 */
public record SendTestEmailResponse(boolean success, String message, String detail) {}
