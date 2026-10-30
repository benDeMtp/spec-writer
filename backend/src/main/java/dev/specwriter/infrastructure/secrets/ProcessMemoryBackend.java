package dev.specwriter.infrastructure.secrets;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trousseau simulé, en mémoire du processus, pour les profils test et e2e : les tests ne touchent
 * jamais au trousseau réel de l'utilisateur.
 */
final class ProcessMemoryBackend implements CredentialBackend {

    private final Map<String, String> values = new ConcurrentHashMap<>();

    @Override
    public void put(String key, String value) {
        values.put(key, value);
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(values.get(key));
    }

    @Override
    public void delete(String key) {
        values.remove(key);
    }
}
