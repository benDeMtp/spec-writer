package fr.kawamind.domain.specification;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import java.time.LocalDate;

/**
 * Point ouvert (021, research.md R2) : une question sans réponse ni valeur par défaut, rattachée à
 * un élément, avec un responsable facultatif. {@code fromCode} : son code dans la version de base,
 * posé par une révision qui le reprend.
 */
public record OpenQuestion(String code, String question, String owner, LocalDate due, Anchor anchor, String fromCode) {

    public static final int MAX_QUESTION = 1_000;
    public static final int MAX_OWNER = 120;

    public OpenQuestion {
        question = question == null ? "" : question.strip();
        if (question.isEmpty() || question.length() > MAX_QUESTION) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Un point ouvert pose une question, en 1 000 caractères au plus.");
        }
        owner = owner == null || owner.isBlank() ? null : owner.strip();
        if (owner != null && owner.length() > MAX_OWNER) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Le responsable d'un point ouvert tient en 120 caractères au plus.");
        }
        fromCode = fromCode == null || fromCode.isBlank() ? null : fromCode.strip();
    }

    public OpenQuestion withCode(String newCode) {
        return new OpenQuestion(newCode, question, owner, due, anchor, fromCode);
    }

    public OpenQuestion withAnchor(Anchor newAnchor) {
        return new OpenQuestion(code, question, owner, due, newAnchor, fromCode);
    }
}
