package fr.kawamind.application.session;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.port.TaskRepository;
import fr.kawamind.domain.task.TaskProgress;
import fr.kawamind.domain.task.TaskTracking;
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
    private final TaskRepository tasks;

    public ChangeStatus(SessionRepository sessions, ClockPort clock) {
        this(sessions, clock, null);
    }

    /**
     * {@code tasks} (015, FR-020a) : avec des tâches suivies, « en cours » et « implémentée » ne se
     * choisissent plus ; repasser en brouillon ou en prête suspend le suivi par les agents.
     */
    public ChangeStatus(SessionRepository sessions, ClockPort clock, TaskRepository tasks) {
        this.sessions = sessions;
        this.clock = clock;
        this.tasks = tasks;
    }

    public Implementation run(UUID sessionId, ImplementationStatus target, boolean confirmUnlock) {
        if (target == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Le statut est obligatoire.");
        }
        Implementation current = sessions.implementation(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        boolean followsTasks = tasks != null && TaskProgress.total(tasks.tasks(sessionId)) > 0;
        if (followsTasks && target != current.status()
                && (target == ImplementationStatus.IN_PROGRESS || target == ImplementationStatus.IMPLEMENTED)) {
            throw new DomainException(ErrorCode.STATUS_FOLLOWS_TASKS);
        }
        Implementation next = current.change(target, ImplementationStatus.Actor.USER, confirmUnlock, clock.now());
        if (!next.equals(current)) {
            sessions.saveImplementation(sessionId, next);
            // repasser en arrière suspend le suivi ; avancer de brouillon à prête ne le touche pas
            boolean back = target == ImplementationStatus.DRAFT || target == ImplementationStatus.READY
                    && current.status() != ImplementationStatus.DRAFT;
            if (followsTasks && back) {
                tasks.setTracking(sessionId, TaskTracking.SUSPENDED);
            }
        }
        return next;
    }
}
