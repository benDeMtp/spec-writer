package dev.specwriter.domain.jira;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lien entre une session et un ticket Jira (data-model.md, LinkedJiraTicket ; FR-042, FR-046 à
 * FR-048). Un ticket au plus par session et une session au plus par ticket.
 */
public record LinkedJiraTicket(
        UUID sessionId,
        String issueKey,
        String issueUrl,
        String issueType,
        int linkedVersionNumber,
        Instant lastKnownUpdated,
        String lastKnownDescriptionHash,
        Instant createdOrImportedAt,
        Instant lastPushedAt,
        SyncState syncState) {

    public static final Pattern KEY = Pattern.compile("[A-Z][A-Z0-9_]+-\\d+");

    public enum SyncState {
        IN_SYNC,
        LOCAL_AHEAD,
        CONFLICT
    }

    public LinkedJiraTicket {
        if (issueKey == null || !KEY.matcher(issueKey).matches()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Clé de ticket Jira invalide.");
        }
    }

    public static boolean isKey(String value) {
        return value != null && KEY.matcher(value).matches();
    }

    /** Une version locale plus récente que la dernière envoyée ou importée. */
    /** Nouvelle version locale. Un conflit en cours le reste : seule sa résolution le lève. */
    public LinkedJiraTicket localAhead() {
        SyncState next = syncState == SyncState.CONFLICT ? SyncState.CONFLICT : SyncState.LOCAL_AHEAD;
        return with(linkedVersionNumber, lastKnownUpdated, lastKnownDescriptionHash, lastPushedAt, next);
    }

    /** Fusion acceptée : l'état Jira lu pour la fusion devient la référence, la version locale reste à envoyer. */
    public LinkedJiraTicket merged(Instant updated, String descriptionHash) {
        return with(linkedVersionNumber, updated, descriptionHash, lastPushedAt, SyncState.LOCAL_AHEAD);
    }

    public LinkedJiraTicket conflict() {
        return with(linkedVersionNumber, lastKnownUpdated, lastKnownDescriptionHash, lastPushedAt, SyncState.CONFLICT);
    }

    /** Après un envoi réussi : l'état Jira relu devient la référence. */
    public LinkedJiraTicket pushed(int version, Instant updated, String descriptionHash, Instant at) {
        return with(version, updated, descriptionHash, at, SyncState.IN_SYNC);
    }

    /** Après la reprise de la version Jira : la version importée devient la référence. */
    public LinkedJiraTicket reimported(int version, Instant updated, String descriptionHash) {
        return with(version, updated, descriptionHash, lastPushedAt, SyncState.IN_SYNC);
    }

    private LinkedJiraTicket with(int version, Instant updated, String hash, Instant pushedAt, SyncState state) {
        return new LinkedJiraTicket(sessionId, issueKey, issueUrl, issueType, version, updated, hash,
                createdOrImportedAt, pushedAt, state);
    }
}
