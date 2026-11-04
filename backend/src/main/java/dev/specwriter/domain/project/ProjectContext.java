package dev.specwriter.domain.project;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Fiche générale d'un projet, en Markdown, rédigée à la main (data-model.md de 002, FR-008,
 * research.md R25). Son hash entre dans l'empreinte des générations (FR-011). Elle est vide par
 * défaut ; une fiche vidée redevient « aucune fiche ».
 */
public record ProjectContext(String markdown, String hash, Instant updatedAt) {

    public static final int MAX_LENGTH = 20_000;
    public static final ProjectContext NONE = new ProjectContext("", "", null);

    public ProjectContext {
        markdown = markdown == null ? "" : markdown;
        if (markdown.length() > MAX_LENGTH) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "La fiche générale dépasse 20 000 caractères.");
        }
        hash = hash == null ? "" : hash;
    }

    /** Fiche modifiée à la main (FR-008). */
    public static ProjectContext edited(String markdown, Instant now) {
        String value = markdown == null ? "" : markdown.strip();
        if (value.length() > MAX_LENGTH) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "La fiche générale dépasse 20 000 caractères.");
        }
        if (value.isEmpty()) {
            return new ProjectContext("", "", now);
        }
        return new ProjectContext(value, sha256(value), now);
    }

    public boolean isEmpty() {
        return markdown.isBlank();
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
