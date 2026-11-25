package fr.kawamind.domain.interview;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.specification.SourceRef;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * « À reporter » (021, US6, FR-027 à FR-030) : une décision de séance qui touche une source de
 * vérité, à reporter à la main dans cette source. L'outil n'y écrit jamais ; il signale seulement
 * que la source a changé depuis la décision. Clos par le mainteneur, jamais automatiquement.
 */
public record SourceUpdate(UUID id, UUID sessionId, UUID interviewId, UUID pointId, SourceRef.Kind kind, String ref,
        String title, String fingerprint, String text, State state, Instant createdAt, Instant closedAt) {

    public static final int MAX_TEXT = 5_000;

    /** Sortes de sources de vérité : pas de document joint, qui n'est pas une source partagée. */
    public static final Set<SourceRef.Kind> KINDS = Set.of(SourceRef.Kind.DOC_PAGE, SourceRef.Kind.JIRA_ISSUE,
            SourceRef.Kind.ARCH_ELEMENT);

    public enum State {
        OPEN,
        CLOSED
    }

    public SourceUpdate {
        Objects.requireNonNull(id);
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(state);
        if (kind == null || !KINDS.contains(kind) || ref == null || ref.isBlank()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Une source à mettre à jour est une page, un ticket ou un élément d'architecture.");
        }
        text = text == null ? "" : text.strip();
        if (text.length() > MAX_TEXT) {
            text = text.substring(0, MAX_TEXT);
        }
    }

    public static SourceUpdate open(UUID id, UUID sessionId, UUID interviewId, UUID pointId, SourceRef.Kind kind,
            String ref, String title, String fingerprint, String text, Instant now) {
        return new SourceUpdate(id, sessionId, interviewId, pointId, kind, ref, title, fingerprint, text, State.OPEN, now,
                null);
    }

    public SourceUpdate closed(Instant now) {
        return new SourceUpdate(id, sessionId, interviewId, pointId, kind, ref, title, fingerprint, text, State.CLOSED,
                createdAt, now);
    }

    /** La source a changé depuis la décision, ou n'est plus indexée : le report est à vérifier. */
    public boolean sourceChanged(String current) {
        return current == null || !current.equals(fingerprint);
    }
}
