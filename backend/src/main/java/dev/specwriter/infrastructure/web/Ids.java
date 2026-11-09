package dev.specwriter.infrastructure.web;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.util.UUID;

/** Identifiants reçus dans les chemins : un identifiant mal formé est traité comme introuvable. */
final class Ids {

    private Ids() {
    }

    static UUID parse(String raw) {
        if (raw == null) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
    }

    /** Identifiant d'un document joint reçu dans un corps : inconnu ou mal formé, la saisie est refusée. */
    static UUID draft(String raw) {
        try {
            return UUID.fromString(raw == null ? "" : raw);
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Document joint inconnu : joignez-le à nouveau.");
        }
    }
}
