package com.tanmaysinghx.portalsso.setup;

import com.tanmaysinghx.portalsso.bootstrap.AdminBootstrapper;
import com.tanmaysinghx.portalsso.security.token.OpaqueTokens;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The one-time key that unlocks the first-run setup wizard, Jenkins LTS style.
 *
 * <p>The wizard creates the most privileged account on the server from an unauthenticated page, so
 * it has to be gated on something only the operator has. The key is written to
 * {@code <portal home>/secrets/initialAdminPassword} and printed to the console — both places only
 * someone with access to the host can read.
 *
 * <ul>
 *   <li>256-bit {@link OpaqueTokens} token; only its SHA-256 is held, and only in memory.
 *   <li>Single use: a successful {@link #consume} clears it and deletes the file, so two racing
 *       submissions cannot both create an administrator.
 *   <li>Lives for one process: a restart before setup is finished issues a fresh key.
 * </ul>
 *
 * <p>Held per node rather than in the database. A cluster should be bootstrapped with
 * {@code APP_BOOTSTRAP_ADMIN_EMAIL} / {@code _PASSWORD} instead, which never issues a key.
 */
@Service
public class SetupKeyService {

    private static final Logger log = LoggerFactory.getLogger(SetupKeyService.class);
    static final String KEY_FILE_NAME = "initialAdminPassword";

    private String keyHash;

    /** Issues a new key, replacing any outstanding one, and writes it to the key file. */
    public synchronized String issue() {
        String key = OpaqueTokens.generate();
        keyHash = OpaqueTokens.hash(key);

        Path keyFile = keyFile();
        try {
            Files.createDirectories(keyFile.getParent());
            Files.writeString(keyFile, key, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.setPosixFilePermissions(keyFile, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Windows filesystem does not support POSIX permissions
            }
        } catch (IOException e) {
            // Not fatal: the key is also printed to the console banner.
            log.warn("Could not write the setup key file {}: {}", keyFile, e.getMessage());
        }
        return key;
    }

    public synchronized boolean isIssued() {
        return keyHash != null;
    }

    /**
     * Checks the key and, if it matches, spends it. Returns false for a wrong key or when none is
     * outstanding; the caller must not create anything in that case. Follow with {@link #complete}
     * once the account is saved, or {@link #restore} if it was not.
     */
    public synchronized boolean consume(String candidate) {
        if (keyHash == null || candidate == null || candidate.isBlank()) {
            return false;
        }
        if (!OpaqueTokens.constantTimeEquals(keyHash, OpaqueTokens.hash(candidate.trim()))) {
            return false;
        }
        keyHash = null;
        return true;
    }

    /** Puts a consumed key back, for when the account it was spent on failed to save. */
    public synchronized void restore(String key) {
        if (keyHash == null) {
            keyHash = OpaqueTokens.hash(key.trim());
        }
    }

    /** Deletes the spent key's file once setup has succeeded. */
    public void complete() {
        try {
            Files.deleteIfExists(keyFile());
        } catch (IOException e) {
            log.warn("Setup is complete but the spent key file {} could not be deleted: {}", keyFile(), e.getMessage());
        }
    }

    public Path keyFile() {
        return AdminBootstrapper.resolvePortalHome().resolve("secrets").resolve(KEY_FILE_NAME);
    }
}
