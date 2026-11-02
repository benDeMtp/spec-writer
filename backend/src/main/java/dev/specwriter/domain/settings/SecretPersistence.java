package dev.specwriter.domain.settings;

import dev.specwriter.domain.port.SecretStore;

/** Où un secret est gardé (FR-033a, FR-033b). */
public enum SecretPersistence {
    KEYCHAIN,
    MEMORY_ONLY;

    public static SecretPersistence from(SecretStore.Persistence persistence) {
        return persistence == SecretStore.Persistence.KEYCHAIN ? KEYCHAIN : MEMORY_ONLY;
    }
}
