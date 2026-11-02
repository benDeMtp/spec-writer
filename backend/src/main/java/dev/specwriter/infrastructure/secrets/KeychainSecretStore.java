package dev.specwriter.infrastructure.secrets;

import dev.specwriter.domain.port.SecretStore;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.jboss.logging.Logger;

/**
 * Secrets dans le trousseau du système (FR-033a), appelé avec un délai maximal pour éviter le
 * blocage connu d'un trousseau verrouillé sous Linux (research.md R8). En cas d'échec, les secrets
 * restent en mémoire le temps de la session (FR-033b) : jamais de repli fichier.
 */
public class KeychainSecretStore implements SecretStore {

    private static final Logger LOG = Logger.getLogger(KeychainSecretStore.class);

    private final Supplier<CredentialBackend> backendFactory;
    private final Duration timeout;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "spec-writer-keychain");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, String> memory = new ConcurrentHashMap<>();

    private volatile CredentialBackend backend;
    private volatile Persistence persistence = Persistence.KEYCHAIN;

    public KeychainSecretStore(Supplier<CredentialBackend> backendFactory, Duration timeout) {
        this.backendFactory = backendFactory;
        this.timeout = timeout;
    }

    public static KeychainSecretStore system() {
        return new KeychainSecretStore(OsKeychainBackend::new, Duration.ofSeconds(5));
    }

    @Override
    public void put(String key, String value) {
        if (persistence == Persistence.KEYCHAIN && call(() -> {
            backend().put(key, value);
            return Boolean.TRUE;
        }).isPresent()) {
            memory.remove(key);
            return;
        }
        memory.put(key, value);
    }

    @Override
    public Optional<String> get(String key) {
        String inMemory = memory.get(key);
        if (inMemory != null) {
            return Optional.of(inMemory);
        }
        if (persistence == Persistence.MEMORY_ONLY) {
            return Optional.empty();
        }
        return call(() -> backend().get(key)).flatMap(o -> o);
    }

    @Override
    public void delete(String key) {
        memory.remove(key);
        if (persistence == Persistence.KEYCHAIN) {
            call(() -> {
                backend().delete(key);
                return Boolean.TRUE;
            });
        }
    }

    @Override
    public Persistence persistence() {
        return persistence;
    }

    private CredentialBackend backend() {
        CredentialBackend b = backend;
        if (b == null) {
            b = backendFactory.get();
            backend = b;
        }
        return b;
    }

    /** Exécute l'appel au trousseau avec délai ; tout échec bascule en mémoire seule. */
    private <T> Optional<T> call(Callable<T> action) {
        Future<T> future = executor.submit(action);
        try {
            return Optional.ofNullable(future.get(timeout.toMillis(), TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            degrade(future, e);
        } catch (Exception e) {
            degrade(future, e);
        }
        return Optional.empty();
    }

    private void degrade(Future<?> future, Exception e) {
        future.cancel(true);
        if (persistence == Persistence.KEYCHAIN) {
            LOG.warnf("Trousseau du système indisponible (%s) : secrets gardés en mémoire jusqu'à la fermeture",
                    e.getClass().getSimpleName());
        }
        persistence = Persistence.MEMORY_ONLY;
    }
}
