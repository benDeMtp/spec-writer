package dev.specwriter.domain.session;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.SessionRepository;
import java.util.UUID;

/**
 * Garde unique des écritures sur une session et ses versions (FR-026a, research.md R7) : une
 * spécification implémentée n'accepte ni réponse, ni version, ni envoi, ni suppression de version.
 * Une règle ArchUnit vérifie que chaque cas d'usage d'écriture l'appelle.
 */
public final class SpecificationLock {

    private SpecificationLock() {
    }

    public static void requireEditable(Implementation implementation) {
        if (implementation.status() == ImplementationStatus.IMPLEMENTED) {
            throw new DomainException(ErrorCode.SPECIFICATION_LOCKED);
        }
    }

    /** Statut relu en base : un passage à « implémentée » pendant une opération est vu. */
    public static void requireEditable(SessionRepository sessions, UUID sessionId) {
        requireEditable(sessions.implementation(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND)));
    }
}
