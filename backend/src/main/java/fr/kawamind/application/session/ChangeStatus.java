package fr.kawamind.application.session;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.session.Implementation;
import fr.kawamind.domain.session.ImplementationStatus;
import java.util.UUID;

/**
 * Statut d'implémentation changé par l'utilisateur (FR-026, FR-026b) : toutes les transitions, la
 * sortie de « implémentée » après confirmation. La suppression et ce changement sont les seules
 * écritures permises sur une spécification verrouillée.
 */
public class ChangeStatus {

    private final SessionRepository sessions;
    private final ClockPort clock;

    public ChangeStatus(SessionRepository sessions, ClockPort clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    public Implementation run(UUID sessionId, ImplementationStatus target, boolean confirmUnlock) {
        if (target == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Le statut est obligatoire.");
        }
        Implementation current = sessions.implementation(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        Implementation next = current.change(target, ImplementationStatus.Actor.USER, confirmUnlock, clock.now());
        if (!next.equals(current)) {
            sessions.saveImplementation(sessionId, next);
        }
        return next;
    }
}
