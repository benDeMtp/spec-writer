package dev.specwriter.infrastructure.secrets;

import java.util.Optional;

/** Accès brut au trousseau du système, isolé pour les tests. */
public interface CredentialBackend {

    void put(String key, String value);

    Optional<String> get(String key);

    void delete(String key);
}
