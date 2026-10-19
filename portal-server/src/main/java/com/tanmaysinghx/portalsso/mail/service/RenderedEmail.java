package com.tanmaysinghx.portalsso.mail.service;

/**
 * A message ready to send: subject, rich part, plain-text part.
 *
 * <p>Both bodies, always. The text part is what a client configured to refuse HTML displays, what a
 * screen reader gets the cleanest reading of, and — practically — the difference between landing in
 * an inbox and landing in spam, since an HTML-only message is a long-standing filter signal.
 */
public record RenderedEmail(String subject, String html, String text) {}
