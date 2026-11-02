package dev.specwriter.support;

import dev.specwriter.domain.port.SecretStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemorySecrets implements SecretStore {
    private final Map<String, String> values = new HashMap<>();
    public Persistence persistence = Persistence.KEYCHAIN;

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

    @Override
    public Persistence persistence() {
        return persistence;
    }
}
