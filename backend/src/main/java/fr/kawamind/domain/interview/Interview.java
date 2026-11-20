package fr.kawamind.domain.interview;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Séance de relecture d'une ou plusieurs spécifications avec les sachants (021, data-model.md) :
 * préparée, puis en cours, puis terminée. Les participants sont des noms saisis librement.
 */
public record Interview(UUID id, UUID projectId, State state, List<String> participants, Instant createdAt,
        Instant startedAt, Instant finishedAt, Cursor cursor) {

    public static final int MAX_PARTICIPANTS = 20;
    public static final int MAX_NAME = 120;

    public enum State {
        PREPARED,
        IN_PROGRESS,
        FINISHED
    }

    /** Dernier élément affiché, pour reprendre la séance (FR-006) ; nul avant le premier déplacement. */
    public record Cursor(UUID sessionId, fr.kawamind.domain.specification.Anchor anchor) {
    }

    public Interview {
        participants = participants(participants);
    }

    public static Interview prepare(UUID id, UUID projectId, List<String> participants, Instant now) {
        return new Interview(id, projectId, State.PREPARED, participants, now, null, null, null);
    }

    /** « Faire le point » : une séance d'une spécification, en cours dès sa création. */
    public static Interview startNow(UUID id, UUID projectId, List<String> participants, Instant now) {
        return new Interview(id, projectId, State.IN_PROGRESS, participants, now, now, null, null);
    }

    public Interview start(Instant now) {
        requirePrepared();
        return new Interview(id, projectId, State.IN_PROGRESS, participants, createdAt, now, null, cursor);
    }

    public Interview finish(Instant now) {
        requireInProgress();
        return new Interview(id, projectId, State.FINISHED, participants, createdAt, startedAt, now, cursor);
    }

    public Interview withParticipants(List<String> names) {
        return new Interview(id, projectId, state, names, createdAt, startedAt, finishedAt, cursor);
    }

    public Interview withCursor(Cursor newCursor) {
        return new Interview(id, projectId, state, participants, createdAt, startedAt, finishedAt, newCursor);
    }

    public void requirePrepared() {
        if (state != State.PREPARED) {
            throw new DomainException(ErrorCode.INTERVIEW_NOT_PREPARED);
        }
    }

    public void requireInProgress() {
        if (state != State.IN_PROGRESS) {
            throw new DomainException(ErrorCode.INTERVIEW_NOT_IN_PROGRESS);
        }
    }

    public void requireFinished() {
        if (state != State.FINISHED) {
            throw new DomainException(ErrorCode.INTERVIEW_NOT_FINISHED);
        }
    }

    /** Noms nettoyés, sans vide ni doublon, dans l'ordre de saisie ; 20 au plus, de 120 caractères au plus. */
    static List<String> participants(List<String> names) {
        LinkedHashSet<String> kept = new LinkedHashSet<>();
        for (String name : names == null ? List.<String>of() : names) {
            String n = name == null ? "" : name.strip();
            if (n.length() > MAX_NAME) {
                throw new DomainException(ErrorCode.VALIDATION_FAILED, "Un nom de participant tient en 120 caractères au plus.");
            }
            if (!n.isEmpty()) {
                kept.add(n);
            }
        }
        if (kept.size() > MAX_PARTICIPANTS) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Une séance compte 20 participants au plus.");
        }
        return List.copyOf(new ArrayList<>(kept));
    }
}
