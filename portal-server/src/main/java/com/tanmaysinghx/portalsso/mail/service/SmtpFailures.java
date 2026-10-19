package com.tanmaysinghx.portalsso.mail.service;

/**
 * Turns a mail exception into something an operator can act on.
 *
 * <p>Shared by the send path and the test-send so the console and the server log describe the same
 * failure the same way. The useful text is almost always on the root cause — Spring's wrapper says
 * "Mail server connection failed" where the cause says "Connection refused" or
 * "535 Authentication failed".
 */
final class SmtpFailures {

    /** Longer than the {@code last_test_error} column, and longer than anyone reads. */
    private static final int MAX_LENGTH = 500;

    private SmtpFailures() {
    }

    static String describe(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String detail = root.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = root.getClass().getSimpleName();
        }
        detail = detail.strip();
        return detail.length() > MAX_LENGTH ? detail.substring(0, MAX_LENGTH) : detail;
    }
}
