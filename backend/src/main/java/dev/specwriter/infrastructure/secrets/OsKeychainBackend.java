package dev.specwriter.infrastructure.secrets;

import com.microsoft.credentialstorage.SecretStore;
import com.microsoft.credentialstorage.StorageProvider;
import com.microsoft.credentialstorage.model.StoredToken;
import com.microsoft.credentialstorage.model.StoredTokenType;
import java.util.Optional;

/**
 * Trousseau du système via {@code io.alapierre:credential-secure-storage} (research.md R8) :
 * Keychain sous macOS, Credential Manager sous Windows, Secret Service sous Linux. Aucun repli
 * vers un stockage non sécurisé : sans trousseau, la construction échoue.
 */
final class OsKeychainBackend implements CredentialBackend {

    static final String SERVICE = "spec-writer";

    private final SecretStore<StoredToken> store;

    OsKeychainBackend() {
        SecretStore<StoredToken> s = StorageProvider.getTokenStorage(true, StorageProvider.SecureOption.REQUIRED);
        if (s == null || !s.isSecure()) {
            throw new IllegalStateException("Aucun trousseau sécurisé disponible");
        }
        this.store = s;
    }

    private static String name(String key) {
        return SERVICE + "/" + key;
    }

    @Override
    public void put(String key, String value) {
        StoredToken token = new StoredToken(value.toCharArray(), StoredTokenType.PERSONAL);
        try {
            if (!store.add(name(key), token)) {
                throw new IllegalStateException("Écriture refusée par le trousseau");
            }
        } finally {
            token.clear();
        }
    }

    @Override
    public Optional<String> get(String key) {
        StoredToken token = store.get(name(key));
        if (token == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new String(token.getValue()));
        } finally {
            token.clear();
        }
    }

    @Override
    public void delete(String key) {
        store.delete(name(key));
    }
}
