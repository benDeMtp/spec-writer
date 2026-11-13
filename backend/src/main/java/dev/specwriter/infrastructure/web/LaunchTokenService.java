package dev.specwriter.infrastructure.web;

import io.quarkus.runtime.LaunchMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Jeton de lancement (256 bits, usage unique), passé dans l'URL ouverte au démarrage, puis échangé
 * contre un cookie de session locale (research.md R14). Tout est en mémoire : un redémarrage
 * invalide les sessions.
 *
 * <p>Une session ouverte peut demander un nouveau jeton pour ouvrir spec-writer dans un autre
 * navigateur (013, T057) : à usage unique lui aussi, il expire après {@value #ISSUED_TTL_SECONDS} s.
 *
 * <p>En dev mode seulement, les sessions sont aussi écrites dans {@code specwriter.dev.session-file}
 * ({@code rw-------}), pour survivre au rechargement à chaud (research.md R17). Le jeton de
 * lancement, lui, n'est jamais écrit.
 */
@ApplicationScoped
public class LaunchTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Logger LOG = Logger.getLogger(LaunchTokenService.class);

    static final long ISSUED_TTL_SECONDS = 120;

    private final String launchToken = random();
    private final AtomicBoolean consumed = new AtomicBoolean();
    private final Set<String> sessions = ConcurrentHashMap.newKeySet();
    private final Map<String, Instant> issued = new ConcurrentHashMap<>();
    private final Optional<Path> store;
    private final Clock clock;

    @Inject
    public LaunchTokenService(@ConfigProperty(name = "specwriter.dev.session-file") Optional<String> sessionFile) {
        this(LaunchMode.current() == LaunchMode.DEVELOPMENT
                ? sessionFile.filter(f -> !f.isBlank()).map(Path::of).orElse(null)
                : null);
    }

    /** @param store fichier des sessions, ou {@code null} pour tout garder en mémoire */
    LaunchTokenService(Path store) {
        this(store, Clock.systemUTC());
    }

    LaunchTokenService(Path store, Clock clock) {
        this.clock = clock;
        this.store = Optional.ofNullable(store);
        this.store.ifPresent(this::load);
    }

    /** Jeton à placer dans l'URL ouverte au démarrage. */
    public String launchToken() {
        return launchToken;
    }

    /** Nouveau jeton à usage unique, pour ouvrir spec-writer dans un autre navigateur (T057). */
    public String issue() {
        Instant now = clock.instant();
        issued.values().removeIf(expiry -> !expiry.isAfter(now));
        String token = random();
        issued.put(token, now.plus(Duration.ofSeconds(ISSUED_TTL_SECONDS)));
        return token;
    }

    /** Échange un jeton de lancement contre une valeur de cookie ; une seule fois par jeton. */
    public Optional<String> exchange(String candidate) {
        if (candidate == null || !(consumeLaunch(candidate) || consumeIssued(candidate))) {
            return Optional.empty();
        }
        String session = random();
        sessions.add(session);
        store.ifPresent(this::save);
        return Optional.of(session);
    }

    private boolean consumeLaunch(String candidate) {
        return constantTimeEquals(candidate, launchToken) && consumed.compareAndSet(false, true);
    }

    private boolean consumeIssued(String candidate) {
        Instant expiry = issued.remove(candidate);
        return expiry != null && expiry.isAfter(clock.instant());
    }

    public boolean isValidSession(String cookie) {
        return cookie != null && sessions.contains(cookie);
    }

    private void load(Path file) {
        try {
            if (Files.isRegularFile(file)) {
                Files.readAllLines(file).stream().map(String::strip).filter(l -> !l.isEmpty()).forEach(sessions::add);
            }
        } catch (IOException e) {
            LOG.warnf("Sessions du dev mode illisibles (%s) : reconnexion nécessaire", e.getClass().getSimpleName());
        }
    }

    private void save(Path file) {
        try {
            if (!Files.exists(file) && Files.getFileStore(file.getParent()).supportsFileAttributeView("posix")) {
                Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            }
            Files.write(file, sessions);
        } catch (IOException | RuntimeException e) {
            LOG.warnf("Sessions du dev mode non enregistrées (%s)", e.getClass().getSimpleName());
        }
    }

    private static String random() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
