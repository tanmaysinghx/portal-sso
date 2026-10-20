package com.tanmaysinghx.portalsso.mail.service;

import com.tanmaysinghx.portalsso.mail.entity.EmailTemplateType;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Renders a notification into a subject and both bodies.
 *
 * <h2>Why subjects are here and not in the templates</h2>
 *
 * <p>A Thymeleaf template could carry its own subject in a {@code <title>}, but pulling it back out
 * means parsing the rendered HTML, and a subject line is the one part of a message that must not
 * depend on the HTML body rendering correctly. Keeping them in Java also means the compiler
 * notices when a new {@link EmailTemplateType} has no subject — the switch below is exhaustive, so
 * adding a constant without a subject will not build.
 */
@Component
public class EmailTemplateRenderer {

    private final TemplateEngine htmlEngine;
    private final TemplateEngine textEngine;

    public EmailTemplateRenderer(
            @Qualifier("emailHtmlTemplateEngine") TemplateEngine htmlEngine,
            @Qualifier("emailTextTemplateEngine") TemplateEngine textEngine) {
        this.htmlEngine = htmlEngine;
        this.textEngine = textEngine;
    }

    /**
     * @param model template variables. {@code brand} is supplied here from {@code branding} so no
     *     caller can forget it and render a message with holes in it.
     */
    public RenderedEmail render(EmailTemplateType type, EmailBranding branding, Map<String, Object> model) {
        Context context = new Context();
        context.setVariables(model);
        context.setVariable("brand", branding);

        String template = type.getTemplate();
        return new RenderedEmail(
                subjectFor(type, branding, model),
                htmlEngine.process(template, context),
                textEngine.process(template, context));
    }

    private static String subjectFor(EmailTemplateType type, EmailBranding branding, Map<String, Object> model) {
        String product = branding.productName();
        return switch (type) {
            case WELCOME -> "Welcome to " + product;
            // Leads with the warning rather than the product name: in a crowded inbox the first
            // few words are all that get read, and this is the one message where acting late
            // actually costs something.
            case NEW_DEVICE_SIGN_IN -> "New sign-in to your " + product + " account from "
                    + string(model, "deviceLabel", "a new device");
            case SIGN_IN_NOTICE -> "You signed in to " + product;
            // No product name first, and no "click here": the subject has to be recognisable at a
            // glance as the thing the user just asked for, since a sign-in link they do not trust
            // is a sign-in link they do not click.
            case MAGIC_LINK_SIGN_IN -> "Your sign-in link for " + product;
            case MAGIC_LINK_SIGN_UP -> "Finish creating your " + product + " account";
            case PASSWORD_RESET -> "Reset your " + product + " password";
        };
    }

    private static String string(Map<String, Object> model, String key, String fallback) {
        Object value = model.get(key);
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }
}
