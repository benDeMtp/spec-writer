package dev.specwriter.infrastructure.web;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.util.UUID;

/** Identifiants reçus dans les chemins : un identifiant mal formé est traité comme introuvable. */
final class Ids {

    private Ids() {
    }

    static UUID parse(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
    }
}
