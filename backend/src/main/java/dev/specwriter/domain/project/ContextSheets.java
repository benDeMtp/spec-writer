package dev.specwriter.domain.project;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Fiches fonctionnelle et technique d'un projet (FR-008a). Leur hash combiné entre dans l'empreinte
 * de génération, et leur date la plus récente est celle du contexte d'une version (FR-011).
 */
public record ContextSheets(ProjectContext functional, ProjectContext technical) {

    public static final ContextSheets NONE = new ContextSheets(ProjectContext.NONE, ProjectContext.NONE);

    public ContextSheets {
        functional = functional == null ? ProjectContext.NONE : functional;
        technical = technical == null ? ProjectContext.NONE : technical;
    }

    public ProjectContext get(ContextSheet sheet) {
        return sheet == ContextSheet.FUNCTIONAL ? functional : technical;
    }

    public ContextSheets with(ContextSheet sheet, ProjectContext context) {
        return sheet == ContextSheet.FUNCTIONAL ? new ContextSheets(context, technical) : new ContextSheets(functional, context);
    }

    public boolean isEmpty() {
        return functional.isEmpty() && technical.isEmpty();
    }

    /** Vide sans aucune fiche ; sinon le SHA-256 des deux hash. */
    public String combinedHash() {
        if (isEmpty()) {
            return "";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((functional.hash() + ":" + technical.hash()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Date de la fiche non vide la plus récente, {@code null} sans fiche. */
    public Instant updatedAt() {
        Instant f = functional.isEmpty() ? null : functional.updatedAt();
        Instant t = technical.isEmpty() ? null : technical.updatedAt();
        if (f == null) {
            return t;
        }
        return t == null || f.isAfter(t) ? f : t;
    }
}
