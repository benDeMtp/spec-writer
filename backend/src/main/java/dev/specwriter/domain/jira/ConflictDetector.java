package dev.specwriter.domain.jira;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Détection de conflit avant un envoi (FR-048) : le ticket a changé si sa date {@code updated} ou
 * l'empreinte de sa description diffèrent des valeurs mémorisées à la dernière lecture.
 */
public final class ConflictDetector {

    private ConflictDetector() {
    }

    public static boolean changedSince(LinkedJiraTicket ticket, Instant currentUpdated, String currentDescription) {
        return !Objects.equals(ticket.lastKnownUpdated(), currentUpdated)
                || !Objects.equals(ticket.lastKnownDescriptionHash(), hash(currentDescription));
    }

    /** SHA-256 de la description Jira, telle que renvoyée par l'API (vide si absente). */
    public static String hash(String description) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((description == null ? "" : description)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
