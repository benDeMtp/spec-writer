package dev.specwriter.infrastructure.web;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Jeton de lancement (256 bits, usage unique), passé dans l'URL ouverte au démarrage, puis échangé
 * contre un cookie de session locale (research.md R14). Tout est en mémoire : un redémarrage
 * invalide les sessions.
 */
@ApplicationScoped
public class LaunchTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String launchToken = random();
    private final AtomicBoolean consumed = new AtomicBoolean();
    private final Set<String> sessions = ConcurrentHashMap.newKeySet();

    /** Jeton à placer dans l'URL ouverte au démarrage. */
    public String launchToken() {
        return launchToken;
    }

    /** Échange le jeton de lancement contre une valeur de cookie ; une seule fois. */
    public Optional<String> exchange(String candidate) {
        if (candidate == null || !constantTimeEquals(candidate, launchToken)) {
            return Optional.empty();
        }
        if (!consumed.compareAndSet(false, true)) {
            return Optional.empty();
        }
        String session = random();
        sessions.add(session);
        return Optional.of(session);
    }

    public boolean isValidSession(String cookie) {
        return cookie != null && sessions.contains(cookie);
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
