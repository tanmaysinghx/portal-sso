package com.tanmaysinghx.portalsso.mail.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ApplicationContext;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Template engines for email, kept separate from the one Spring Boot autoconfigures for MVC.
 *
 * <h2>Why not reuse the MVC engine</h2>
 *
 * <p>Adding resolvers to the autoconfigured {@code SpringTemplateEngine} would put email templates
 * on the same resolver chain as the sign-in and consent pages — a page name colliding with an email
 * name would then resolve to whichever resolver ran first, and a misconfiguration here could stop
 * the login page rendering. The two have no reason to share anything: email needs no Spring
 * Security dialect, no request context, and a different template mode for the text part.
 *
 * <p>Two engines rather than one with two resolvers so {@code process("welcome", ...)} is
 * unambiguous: one engine only ever resolves HTML, the other only ever text, and neither can
 * silently fall through to the other when a template is missing.
 */
@Configuration
public class EmailTemplateConfig {

    /** Rich bodies: {@code templates/email/<name>.html}. */
    @Bean
    public TemplateEngine emailHtmlTemplateEngine(ApplicationContext applicationContext) {
        return engine(applicationContext, "templates/email/", ".html", TemplateMode.HTML);
    }

    /** Plain-text alternatives: {@code templates/email/text/<name>.txt}. */
    @Bean
    public TemplateEngine emailTextTemplateEngine(ApplicationContext applicationContext) {
        return engine(applicationContext, "templates/email/text/", ".txt", TemplateMode.TEXT);
    }

    /**
     * {@link SpringTemplateEngine} rather than the plain {@link TemplateEngine}, which matters for a
     * non-obvious reason: the plain engine evaluates expressions with OGNL, and the Spring Boot
     * Thymeleaf starter does not put OGNL on the classpath because its own engine uses SpEL. A plain
     * engine therefore builds fine and fails at first render with {@code ClassNotFoundException:
     * ognl.ClassResolver}. Using the Spring engine keeps email on the same expression language as
     * the rest of the templates, with no extra dependency.
     */
    private static TemplateEngine engine(
            ApplicationContext applicationContext, String prefix, String suffix, TemplateMode mode) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix(prefix);
        resolver.setSuffix(suffix);
        resolver.setTemplateMode(mode);
        resolver.setCharacterEncoding("UTF-8");
        // Templates are packaged inside the jar and cannot change at runtime, so caching is pure
        // win — rendering happens on the sign-in path, where every avoidable millisecond counts.
        resolver.setCacheable(true);
        // A missing template must fail loudly here rather than resolving to empty output; an email
        // sent with a blank body is worse than one that errors and gets logged.
        resolver.setCheckExistence(true);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateEngineMessageSource(applicationContext);
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
