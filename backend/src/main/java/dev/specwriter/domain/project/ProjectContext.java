package dev.specwriter.domain.project;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Fiche de contexte d'un projet, en Markdown (data-model.md de 002, FR-008). Son hash entre dans
 * l'empreinte des générations (FR-011). Elle est vide par défaut ; une fiche vidée redevient
 * « aucun contexte ».
 */
public record ProjectContext(String markdown, String hash, Origin origin, Instant updatedAt) {

    public enum Origin { NONE, GENERATED, EDITED }

    public static final int MAX_LENGTH = 20_000;
    public static final ProjectContext NONE = new ProjectContext("", "", Origin.NONE, null);

    public ProjectContext {
        markdown = markdown == null ? "" : markdown;
        if (markdown.length() > MAX_LENGTH) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Le contexte du projet dépasse 20 000 caractères.");
        }
        hash = hash == null ? "" : hash;
    }

    /** Fiche acceptée depuis une proposition de l'IA (FR-013a). */
    public static ProjectContext generated(String markdown, Instant now) {
        return of(markdown, Origin.GENERATED, now);
    }

    /** Fiche modifiée à la main (FR-013c). */
    public static ProjectContext edited(String markdown, Instant now) {
        return of(markdown, Origin.EDITED, now);
    }

    public boolean isEmpty() {
        return markdown.isBlank();
    }

    private static ProjectContext of(String markdown, Origin origin, Instant now) {
        String value = markdown == null ? "" : markdown.strip();
        if (value.length() > MAX_LENGTH) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Le contexte du projet dépasse 20 000 caractères.");
        }
        if (value.isEmpty()) {
            return new ProjectContext("", "", Origin.NONE, now);
        }
        return new ProjectContext(value, sha256(value), origin, now);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
