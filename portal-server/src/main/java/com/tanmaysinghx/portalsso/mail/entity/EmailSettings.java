package com.tanmaysinghx.portalsso.mail.entity;

import com.tanmaysinghx.portalsso.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * How mail looks and which messages are sent — one row, edited by an administrator.
 *
 * <h2>Why this is separate from {@code SmtpSettings}</h2>
 *
 * <p>{@code SmtpSettings} is <em>how mail is delivered</em>: host, port, credentials. This is
 * <em>what gets sent and how it looks</em>. Keeping them apart means an operator can move to a new
 * relay without reviewing templates, and switch a notification off without going near the relay —
 * and the audit entries for the two read as the different kinds of change they are.
 *
 * <h2>Why branding is duplicated here</h2>
 *
 * <p>The console's own branding ({@code BrandingService}) lives in the administrator's browser
 * {@code localStorage}, so the server cannot see it. Mail is rendered server-side, so it needs its
 * own copy of the product name, logo and accent colour. The alternative — moving console branding
 * server-side — is a bigger change than this feature needs, and these two surfaces do legitimately
 * differ: an email logo has to be an absolute URL a mail client can fetch, while the console can
 * use an inline data URI.
 */
@Entity
@Table(name = "email_settings")
public class EmailSettings extends BaseEntity {

    public static final String SINGLETON_KEY = "DEFAULT";

    /** Used wherever no product name has been set, and as the console's placeholder. */
    public static final String DEFAULT_PRODUCT_NAME = "Portal SSO";

    /** The logo amber from the design tokens — see {@code portal-client/src/styles.css}. */
    public static final String DEFAULT_ACCENT_COLOR = "#F59E0B";

    public static final int DEFAULT_MAGIC_LINK_TTL_MINUTES = 15;

    @Column(name = "config_key", nullable = false, length = 20)
    private String configKey = SINGLETON_KEY;

    @Column(name = "product_name", length = 100)
    private String productName;

    @Column(name = "logo_url", length = 1000)
    private String logoUrl;

    @Column(name = "accent_color", length = 9)
    private String accentColor;

    @Column(name = "support_url", length = 1000)
    private String supportUrl;

    @Column(name = "footer_note", length = 500)
    private String footerNote;

    @Column(name = "welcome_enabled", nullable = false)
    private boolean welcomeEnabled = true;

    @Column(name = "new_device_enabled", nullable = false)
    private boolean newDeviceEnabled = true;

    @Column(name = "sign_in_notice_enabled", nullable = false)
    private boolean signInNoticeEnabled = false;

    @Column(name = "magic_link_enabled", nullable = false)
    private boolean magicLinkEnabled = false;

    @Column(name = "magic_link_registration_enabled", nullable = false)
    private boolean magicLinkRegistrationEnabled = false;

    @Column(name = "magic_link_ttl_minutes", nullable = false)
    private int magicLinkTtlMinutes = DEFAULT_MAGIC_LINK_TTL_MINUTES;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    public String getConfigKey() {
        return configKey;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getAccentColor() {
        return accentColor;
    }

    public void setAccentColor(String accentColor) {
        this.accentColor = accentColor;
    }

    public String getSupportUrl() {
        return supportUrl;
    }

    public void setSupportUrl(String supportUrl) {
        this.supportUrl = supportUrl;
    }

    public String getFooterNote() {
        return footerNote;
    }

    public void setFooterNote(String footerNote) {
        this.footerNote = footerNote;
    }

    public boolean isWelcomeEnabled() {
        return welcomeEnabled;
    }

    public void setWelcomeEnabled(boolean welcomeEnabled) {
        this.welcomeEnabled = welcomeEnabled;
    }

    public boolean isNewDeviceEnabled() {
        return newDeviceEnabled;
    }

    public void setNewDeviceEnabled(boolean newDeviceEnabled) {
        this.newDeviceEnabled = newDeviceEnabled;
    }

    public boolean isSignInNoticeEnabled() {
        return signInNoticeEnabled;
    }

    public void setSignInNoticeEnabled(boolean signInNoticeEnabled) {
        this.signInNoticeEnabled = signInNoticeEnabled;
    }

    public boolean isMagicLinkEnabled() {
        return magicLinkEnabled;
    }

    public void setMagicLinkEnabled(boolean magicLinkEnabled) {
        this.magicLinkEnabled = magicLinkEnabled;
    }

    public boolean isMagicLinkRegistrationEnabled() {
        return magicLinkRegistrationEnabled;
    }

    public void setMagicLinkRegistrationEnabled(boolean magicLinkRegistrationEnabled) {
        this.magicLinkRegistrationEnabled = magicLinkRegistrationEnabled;
    }

    public int getMagicLinkTtlMinutes() {
        return magicLinkTtlMinutes;
    }

    public void setMagicLinkTtlMinutes(int magicLinkTtlMinutes) {
        this.magicLinkTtlMinutes = magicLinkTtlMinutes;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    /** Whether the given notification should be sent, before the relay is even consulted. */
    public boolean isEnabled(EmailTemplateType type) {
        return switch (type) {
            case WELCOME -> welcomeEnabled;
            case NEW_DEVICE_SIGN_IN -> newDeviceEnabled;
            case SIGN_IN_NOTICE -> signInNoticeEnabled;
            // Both magic-link mails are governed by the feature switch rather than by a
            // notification toggle: a link the user explicitly asked for is not a notification, and
            // suppressing it would break the flow rather than quieten it.
            case MAGIC_LINK_SIGN_IN, MAGIC_LINK_SIGN_UP -> magicLinkEnabled;
        };
    }
}
