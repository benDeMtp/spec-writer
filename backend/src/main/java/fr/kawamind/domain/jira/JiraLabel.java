package fr.kawamind.domain.jira;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;

/**
 * Label Jira de reconnaissance des tickets de spec-writer (024, research R4) : de 1 à 255
 * caractères, sans blanc, comparé tel quel (Jira distingue la casse).
 */
public record JiraLabel(String value) {

    public static final int MAX = 255;
    private static final String INVALID = "Un label Jira ne contient pas d'espace et compte au plus " + MAX
            + " caractères.";

    public JiraLabel {
        if (value == null || value.isEmpty() || value.length() > MAX || value.codePoints().anyMatch(Character::isWhitespace)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, INVALID);
        }
    }

    public static JiraLabel of(String raw) {
        return new JiraLabel(raw == null ? null : raw.strip());
    }

    @Override
    public String toString() {
        return value;
    }
}
