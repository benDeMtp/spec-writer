package dev.specwriter.domain.project;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.util.regex.Pattern;

/** Clé d'un projet Jira, au format {@code [A-Z][A-Z0-9_]{1,9}} (data-model.md de 002, FR-006). */
public record JiraProjectKey(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z][A-Z0-9_]{1,9}");

    public JiraProjectKey {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "La clé du projet Jira doit faire 2 à 10 caractères : des majuscules, des chiffres ou « _ ».");
        }
    }

    /** Clé facultative : une saisie vide signifie « pas de clé propre au projet ». */
    public static JiraProjectKey ofNullable(String raw) {
        return raw == null || raw.isBlank() ? null : new JiraProjectKey(raw.strip());
    }
}
