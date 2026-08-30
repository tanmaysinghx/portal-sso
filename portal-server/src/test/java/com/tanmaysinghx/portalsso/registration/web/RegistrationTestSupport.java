package com.tanmaysinghx.portalsso.registration.web;

import java.util.UUID;

/** Shared helpers. Deliberately annotation-free so it carries no Spring test context of its own. */
final class RegistrationTestSupport {

    private RegistrationTestSupport() {}

    static String uniqueEmail() {
        return "signup-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    static String body(String email) {
        return """
               {"email":"%s","password":"CorrectHorse123!","firstName":"Ada","lastName":"Lovelace"}
               """
                .formatted(email);
    }
}
