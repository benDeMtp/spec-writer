package dev.specwriter.domain.project;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.util.Locale;

/** Nom d'un projet : 1 à 80 caractères, sans espace en début ni en fin (data-model.md de 002, FR-001). */
public record ProjectName(String value) {

    public static final int MAX_LENGTH = 80;

    public ProjectName {
        if (value == null || value.isEmpty() || value.length() > MAX_LENGTH || !value.equals(value.strip())) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Le nom du projet doit faire entre 1 et 80 caractères.");
        }
    }

    public static ProjectName of(String raw) {
        return new ProjectName(raw == null ? null : raw.strip());
    }

    /** Clé d'unicité : deux noms qui ne diffèrent que par la casse désignent le même projet. */
    public String nameKey() {
        return value.toLowerCase(Locale.ROOT);
    }
}
