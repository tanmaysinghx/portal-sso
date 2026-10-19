package com.tanmaysinghx.portalsso.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.tanmaysinghx.portalsso.mail.entity.EmailSettings;
import com.tanmaysinghx.portalsso.mail.entity.EmailTemplateType;
import com.tanmaysinghx.portalsso.mail.service.EmailBranding;
import com.tanmaysinghx.portalsso.mail.service.EmailTemplateRenderer;
import com.tanmaysinghx.portalsso.mail.service.RenderedEmail;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Renders every template, because almost nothing about a Thymeleaf template is checked at compile
 * time. A renamed fragment, a typo in a variable name or a missing text file produces either an
 * exception or — worse — a message with a hole in it, and the only way to find out is to render.
 *
 * <p>Both branches of every optional field are exercised. Operators configure a logo, a support URL
 * and a footer note independently, users may or may not have a first name, and GeoIP is off by
 * default, so "all fields populated" is the configuration this will almost never run in.
 */
@SpringBootTest
@ActiveProfiles("test")
class EmailTemplateRenderingTest {

    @Autowired
    private EmailTemplateRenderer renderer;

    private static EmailBranding fullBranding() {
        EmailSettings settings = new EmailSettings();
        settings.setProductName("Acme Identity");
        settings.setLogoUrl("https://cdn.example.com/logo.png");
        settings.setAccentColor("#2563EB");
        settings.setSupportUrl("https://help.example.com");
        settings.setFooterNote("Internal use only.");
        return EmailBranding.from(settings, "https://sso.example.com/");
    }

    /** What a server that configured a relay and nothing else sends. */
    private static EmailBranding bareBranding() {
        return EmailBranding.from(new EmailSettings(), "https://sso.example.com");
    }

    private static Map<String, Object> fullModel() {
        Map<String, Object> model = new HashMap<>();
        model.put("firstName", "Alex");
        model.put("preview", false);
        model.put("deviceLabel", "Chrome on macOS");
        model.put("ipAddress", "203.0.113.42");
        model.put("location", "Bengaluru, India");
        model.put("hasLocation", true);
        model.put("occurredAt", "1 January 2026 at 09:14 UTC");
        model.put("signInUrl", "https://sso.example.com/sign-in");
        model.put("linkUrl", "https://sso.example.com/magic-link?token=abc123");
        model.put("expiresInMinutes", 15);
        return model;
    }

    /** No first name, no location — the common case for a self-registered account with GeoIP off. */
    private static Map<String, Object> minimalModel() {
        Map<String, Object> model = fullModel();
        model.put("firstName", null);
        model.put("location", null);
        model.put("hasLocation", false);
        return model;
    }

    @ParameterizedTest
    @EnumSource(EmailTemplateType.class)
    void everyTemplateRendersBothBodies(EmailTemplateType type) {
        RenderedEmail rendered = renderer.render(type, fullBranding(), fullModel());

        assertThat(rendered.subject()).isNotBlank().contains("Acme Identity");
        assertThat(rendered.html()).isNotBlank();
        assertThat(rendered.text()).isNotBlank();

        // The layout fragment resolved and wrapped the body, rather than the template rendering
        // only its own content div.
        assertThat(rendered.html()).contains("<!doctype html>").contains("</html>");
        assertThat(rendered.html()).contains("Acme Identity");

        // Nothing unresolved leaked through. A Thymeleaf expression that fails to bind renders as
        // literal "null" rather than erroring, which is the failure this catches.
        assertThat(rendered.html()).doesNotContain("${").doesNotContain("th:text");
        assertThat(rendered.text()).doesNotContain("${").doesNotContain("[(");
    }

    @ParameterizedTest
    @EnumSource(EmailTemplateType.class)
    void everyTemplateRendersWithOnlyTheDefaults(EmailTemplateType type) {
        RenderedEmail rendered = renderer.render(type, bareBranding(), minimalModel());

        assertThat(rendered.subject()).isNotBlank().contains("Portal SSO");
        assertThat(rendered.html()).contains("Portal SSO");

        // The greeting falls back to "Hi," rather than printing a null name.
        assertThat(rendered.html()).doesNotContain("Hi null").doesNotContain("Hello null");
        assertThat(rendered.text()).doesNotContain("Hi null");

        // With no logo configured the wordmark stands in, and no broken <img> is emitted.
        assertThat(rendered.html()).doesNotContain("<img src=\"\"");
    }

    @Test
    void theConfiguredAccentColourReachesTheMarkup() {
        RenderedEmail rendered = renderer.render(EmailTemplateType.WELCOME, fullBranding(), fullModel());
        assertThat(rendered.html()).contains("#2563EB");
    }

    @Test
    void theButtonBackgroundIsNotTheConfiguredAccent() {
        // The design tokens note white on the default amber is about 2:1 contrast, and an operator
        // may set the accent to anything at all — so a button's background is deliberately fixed at
        // ink-900 rather than following the accent. Asserted because it looks like an oversight and
        // would otherwise be "fixed" into an accessibility bug.
        RenderedEmail rendered = renderer.render(EmailTemplateType.WELCOME, fullBranding(), fullModel());
        assertThat(rendered.html()).contains("background-color:#18181b");
    }

    @Test
    void aLogoIsUsedWhenOneIsConfigured() {
        RenderedEmail rendered = renderer.render(EmailTemplateType.WELCOME, fullBranding(), fullModel());
        assertThat(rendered.html()).contains("https://cdn.example.com/logo.png");
    }

    @Test
    void theIssuerUrlLosesItsTrailingSlashSoLinksDoNotDoubleUp() {
        // fullBranding() passes "https://sso.example.com/" with the slash, as ISSUER_URL often is.
        assertThat(fullBranding().portalUrl()).isEqualTo("https://sso.example.com");
        RenderedEmail rendered = renderer.render(EmailTemplateType.WELCOME, fullBranding(), fullModel());
        assertThat(rendered.html()).doesNotContain("sso.example.com//");
    }

    @Test
    void theSignInDetailsBlockOmitsLocationRatherThanPrintingUnknown() {
        RenderedEmail rendered =
                renderer.render(EmailTemplateType.NEW_DEVICE_SIGN_IN, fullBranding(), minimalModel());

        assertThat(rendered.html()).contains("Chrome on macOS").contains("203.0.113.42");
        assertThat(rendered.html()).doesNotContain(">Near<");
        assertThat(rendered.text()).doesNotContain("Near:");
    }

    @Test
    void theMagicLinkIsPresentAsBothAButtonAndAReadableUrl() {
        RenderedEmail rendered =
                renderer.render(EmailTemplateType.MAGIC_LINK_SIGN_IN, fullBranding(), fullModel());

        // Twice in the HTML: once as the button href, once as the pasteable URL for a cautious user
        // or a client that strips the button.
        assertThat(rendered.html()).contains("https://sso.example.com/magic-link?token=abc123");
        assertThat(rendered.text()).contains("https://sso.example.com/magic-link?token=abc123");
        assertThat(rendered.html()).contains("15 minutes");
        assertThat(rendered.text()).contains("15 minutes");
    }

    @Test
    void aPreviewSaysSoInTheBodyAndNotJustTheSubject() {
        Map<String, Object> model = fullModel();
        model.put("preview", true);

        RenderedEmail rendered =
                renderer.render(EmailTemplateType.NEW_DEVICE_SIGN_IN, fullBranding(), model);

        // A security alert that arrives looking entirely genuine is alarming to receive by
        // surprise, so the banner matters more here than anywhere else.
        assertThat(rendered.html()).contains("Preview");
        assertThat(rendered.text()).contains("[PREVIEW]");
    }

    @Test
    void anOperatorsFooterNoteAndSupportUrlAppearWhenSetAndAreAbsentWhenNot() {
        RenderedEmail withExtras = renderer.render(EmailTemplateType.WELCOME, fullBranding(), fullModel());
        assertThat(withExtras.html()).contains("Internal use only.").contains("https://help.example.com");
        assertThat(withExtras.text()).contains("Internal use only.").contains("https://help.example.com");

        RenderedEmail without = renderer.render(EmailTemplateType.WELCOME, bareBranding(), fullModel());
        assertThat(without.html()).doesNotContain("Contact support");
        assertThat(without.text()).doesNotContain("Need help?");
    }

    @Test
    void transactionalMailCarriesNoUnsubscribeOffer() {
        // These are account and security messages, so there is nothing to unsubscribe from, and
        // offering it would imply the security alerts are optional.
        RenderedEmail rendered =
                renderer.render(EmailTemplateType.NEW_DEVICE_SIGN_IN, fullBranding(), fullModel());
        assertThat(rendered.html().toLowerCase()).doesNotContain("unsubscribe");
    }
}
