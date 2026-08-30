package com.tanmaysinghx.portalsso.analytics.geo;

import com.maxmind.geoip2.DatabaseReader;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.net.InetAddress;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Turns a login IP into a country for the dashboard map.
 *
 * <p>Resolution needs a MaxMind GeoLite2/GeoIP2 database, which their licence requires each
 * operator to download themselves, so no file ships with this project. Point
 * {@code app.geoip.database-path} at a {@code GeoLite2-Country.mmdb} to switch it on. With no file
 * configured every public address resolves to unknown and the map stays empty rather than the
 * application failing to start.
 *
 * <p>Private and loopback addresses are labelled without consulting the database at all — they
 * have no country, and during local development every login comes from one, so reporting them
 * honestly beats reporting nothing.
 */
@Component
public class GeoIpResolver {

    private static final Logger log = LoggerFactory.getLogger(GeoIpResolver.class);

    /** Not a real country: a bucket for addresses that cannot have one. */
    public static final GeoLocation LOCAL = new GeoLocation(null, "Local network", null);
    public static final GeoLocation UNKNOWN = new GeoLocation(null, "Unknown", null);

    private final DatabaseReader reader;

    public GeoIpResolver(@Value("${app.geoip.database-path:}") String databasePath) {
        this.reader = openDatabase(databasePath);
    }

    private static DatabaseReader openDatabase(String databasePath) {
        if (databasePath == null || databasePath.isBlank()) {
            log.info("GeoIP database not configured (app.geoip.database-path); login geography will show as Unknown.");
            return null;
        }
        File file = new File(databasePath);
        if (!file.isFile()) {
            // A misconfigured path must not take the whole application down over a dashboard panel.
            log.warn("GeoIP database not found at '{}'; login geography will show as Unknown.", databasePath);
            return null;
        }
        try {
            DatabaseReader opened = new DatabaseReader.Builder(file).withCache(new com.maxmind.db.CHMCache()).build();
            log.info("GeoIP database loaded from '{}'.", databasePath);
            return opened;
        } catch (Exception e) {
            log.warn("Could not open GeoIP database at '{}': {}", databasePath, e.getMessage());
            return null;
        }
    }

    public GeoLocation resolve(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return UNKNOWN;
        }
        try {
            InetAddress address = InetAddress.getByName(ipAddress);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                    || address.isAnyLocalAddress()) {
                return LOCAL;
            }
            if (reader == null) {
                return UNKNOWN;
            }
            return Optional.ofNullable(reader.tryCountry(address).orElse(null))
                    .map(response -> new GeoLocation(
                            response.getCountry().getIsoCode(), response.getCountry().getName(), null))
                    .orElse(UNKNOWN);
        } catch (Exception e) {
            // Unparseable address, or a lookup miss — neither is worth failing a sign-in over.
            return UNKNOWN;
        }
    }

    public boolean isDatabaseAvailable() {
        return reader != null;
    }

    @PreDestroy
    void close() {
        if (reader != null) {
            try {
                reader.close();
            } catch (Exception ignored) {
                // Shutting down; nothing useful to do.
            }
        }
    }

    /** @param code ISO 3166-1 alpha-2, or null when the address has no country. */
    public record GeoLocation(String code, String name, String city) {}
}
