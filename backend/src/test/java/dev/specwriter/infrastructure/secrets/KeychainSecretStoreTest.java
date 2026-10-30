package dev.specwriter.infrastructure.secrets;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.port.SecretStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class KeychainSecretStoreTest {

    static class MapBackend implements CredentialBackend {
        final Map<String, String> values = new HashMap<>();

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

    @Test
    void ecritPuisRelitDansLeTrousseau() {
        MapBackend backend = new MapBackend();
        KeychainSecretStore store = new KeychainSecretStore(() -> backend, Duration.ofSeconds(1));
        store.put(SecretStore.JIRA_PAT, "TEST-JIRA-PAT");
        assertThat(backend.values).containsEntry(SecretStore.JIRA_PAT, "TEST-JIRA-PAT");
        assertThat(store.get(SecretStore.JIRA_PAT)).contains("TEST-JIRA-PAT");
        store.delete(SecretStore.JIRA_PAT);
        assertThat(store.get(SecretStore.JIRA_PAT)).isEmpty();
        assertThat(store.persistence()).isEqualTo(SecretStore.Persistence.KEYCHAIN);
    }

    @Test
    void trousseauBloqueBasculeEnMemoire() {
        CountDownLatch never = new CountDownLatch(1);
        CredentialBackend blocking = new MapBackend() {
            @Override
            public void put(String key, String value) {
                try {
                    never.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        KeychainSecretStore store = new KeychainSecretStore(() -> blocking, Duration.ofMillis(200));
        store.put(SecretStore.JIRA_PAT, "TEST-JIRA-PAT");
        assertThat(store.persistence()).isEqualTo(SecretStore.Persistence.MEMORY_ONLY);
        assertThat(store.get(SecretStore.JIRA_PAT)).contains("TEST-JIRA-PAT");
    }

    @Test
    void trousseauAbsentBasculeEnMemoire() {
        KeychainSecretStore store = new KeychainSecretStore(() -> {
            throw new IllegalStateException("Aucun trousseau sécurisé disponible");
        }, Duration.ofSeconds(1));
        store.put(SecretStore.JIRA_PAT, "TEST-JIRA-PAT");
        assertThat(store.persistence()).isEqualTo(SecretStore.Persistence.MEMORY_ONLY);
        assertThat(store.get(SecretStore.JIRA_PAT)).contains("TEST-JIRA-PAT");
        store.delete(SecretStore.JIRA_PAT);
        assertThat(store.get(SecretStore.JIRA_PAT)).isEmpty();
    }

    @Test
    void aucunFichierCree() throws Exception {
        Path dataDir = Path.of("target/test-data");
        Files.createDirectories(dataDir);
        long before = count(dataDir);
        KeychainSecretStore store = new KeychainSecretStore(() -> {
            throw new IllegalStateException("absent");
        }, Duration.ofSeconds(1));
        store.put(SecretStore.JIRA_PAT, "TEST-JIRA-PAT");
        assertThat(count(dataDir)).isEqualTo(before);
        try (Stream<Path> files = Files.walk(dataDir)) {
            assertThat(files.filter(Files::isRegularFile).filter(f -> !f.toString().endsWith(".db")
                    && !f.toString().contains(".db-")).anyMatch(KeychainSecretStoreTest::containsToken)).isFalse();
        }
    }

    private static long count(Path dir) throws Exception {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.count();
        }
    }

    private static boolean containsToken(Path f) {
        try {
            return Files.readString(f).contains("TEST-JIRA-PAT");
        } catch (Exception e) {
            return false;
        }
    }
}
