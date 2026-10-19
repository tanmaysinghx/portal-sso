package com.tanmaysinghx.portalsso.security.magiclink.entity;

/**
 * What a magic link is allowed to do.
 *
 * <p>Stored on the token and checked at redeem time, so a link minted to create an account can
 * never be replayed to sign in to an existing one, or the reverse. Without this the two flows would
 * share one credential and the weaker of the two would set the security of both.
 */
public enum MagicLinkPurpose {

    /** Signs in an account that already exists. */
    SIGN_IN,

    /**
     * Creates the account and signs it in. Issued only when magic-link registration is switched on,
     * and only for an address that does not yet have an account.
     */
    SIGN_UP
}
