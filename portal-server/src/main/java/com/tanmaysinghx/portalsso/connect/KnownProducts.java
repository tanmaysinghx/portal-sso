package com.tanmaysinghx.portalsso.connect;

import java.util.Map;
import java.util.Optional;

/**
 * Products from the Portal SSO ecosystem that this server recognises by their manifest's
 * {@code product} id.
 *
 * <p>Recognition only picks a display name and catalog category. It is <strong>not</strong> proof
 * of identity: any app can claim any product id. What the administrator approves is the app's
 * address, which the one-time delivery code is bound to, and the approval page says so.
 */
public final class KnownProducts {

    public record Product(String id, String displayName, String category, String tagline) {}

    private static final Map<String, Product> PRODUCTS = Map.of(
            "eggless-observability",
            new Product("eggless-observability", "Eggless Observability", "Observability",
                    "Logs, live tail and alerts for your applications"));

    private KnownProducts() {}

    public static Optional<Product> find(String productId) {
        return productId == null ? Optional.empty() : Optional.ofNullable(PRODUCTS.get(productId));
    }
}
