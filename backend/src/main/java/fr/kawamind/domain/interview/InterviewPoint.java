package fr.kawamind.domain.interview;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.specification.Anchor;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Ce qui est noté en séance (021, data-model.md), rattaché à un élément de la version relue : la
 * réponse à une hypothèse, un écart ou un point ouvert de la spécification, une remarque, ou une
 * question qu'on ne sait pas trancher (toujours reportée). {@code source} : source de vérité
 * désignée à la main (FR-027).
 */
public record InterviewPoint(UUID id, UUID interviewId, UUID sessionId, Anchor anchor, Origin origin, String originCode,
        String text, Outcome outcome, String owner, LocalDate due, DesignatedSource source, Instant createdAt,
        Instant updatedAt) {

    public static final int MAX_TEXT = 5_000;
    public static final int MAX_OWNER = 120;
    /** Points au plus par spécification et par séance. */
    public static final int MAX_PER_SPECIFICATION = 200;

    public enum Origin {
        ASSUMPTION,
        SOURCE_DEVIATION,
        OPEN_QUESTION,
        REMARK,
        QUESTION;

        /** Point de la spécification (hypothèse, écart, point ouvert), désigné par son code. */
        public boolean fromSpecification() {
            return this == ASSUMPTION || this == SOURCE_DEVIATION || this == OPEN_QUESTION;
        }
    }

    public enum Outcome {
        CONFIRMED,
        CHANGE,
        REMOVE,
        DEFERRED
    }

    /** Source de vérité désignée à la main : élément de la connaissance du projet. */
    public record DesignatedSource(String kind, String ref) {
    }

    public InterviewPoint {
        text = text == null ? "" : text.strip();
        if (text.length() > MAX_TEXT) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Un point tient en 5 000 caractères au plus.");
        }
        if (origin == Origin.QUESTION) {
            if (outcome != null && outcome != Outcome.DEFERRED) {
                throw new DomainException(ErrorCode.VALIDATION_FAILED, "Une question notée en séance reste à vérifier.");
            }
            outcome = Outcome.DEFERRED;
        }
        boolean textRequired = (outcome == Outcome.CHANGE || outcome == Outcome.DEFERRED)
                || origin == Origin.REMARK && outcome == null;
        if (textRequired && text.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Écrivez ce qui a été dit avant d'enregistrer.");
        }
        if (origin.fromSpecification()) {
            if (originCode == null || originCode.isBlank()) {
                throw new DomainException(ErrorCode.VALIDATION_FAILED, "Ce point ne désigne aucun élément de la spécification.");
            }
            originCode = originCode.strip();
        } else {
            originCode = null;
        }
        owner = owner == null || owner.isBlank() ? null : owner.strip();
        if (owner != null && owner.length() > MAX_OWNER) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Le nom du responsable tient en 120 caractères au plus.");
        }
    }

    /** Tranché : confirmé, à modifier ou à retirer ; un point reporté reste ouvert. */
    public boolean decided() {
        return outcome == Outcome.CONFIRMED || outcome == Outcome.CHANGE || outcome == Outcome.REMOVE;
    }

    public static void requireRoom(int existing) {
        if (existing >= MAX_PER_SPECIFICATION) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Une séance note au plus 200 points par spécification.");
        }
    }
}
