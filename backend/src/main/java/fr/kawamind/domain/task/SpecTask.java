package fr.kawamind.domain.task;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.jira.LinkedJiraTicket;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Une étape d'implémentation d'une spécification (015, data-model.md). L'état vit dans spec-writer
 * et dans la sous-tâche Jira ; la relecture de Jira s'impose (R6). Titres, résultats et comptes
 * rendus sont des données : jamais journalisés, jamais des consignes.
 *
 * @param pendingTarget  transition enregistrée mais pas encore dans Jira (« à synchroniser », R5)
 * @param pendingComment le compte rendu reste à envoyer en commentaire
 * @param dependsOn      tâches préalables, de la même spécification
 */
public record SpecTask(UUID id, UUID sessionId, int position, String title, String outcome, Basis basis,
        TaskOrigin origin, TaskCreation creation, String jiraKey, TaskStatus status, String jiraStatusName,
        Holder holder, TaskStatus pendingTarget, boolean pendingComment, String report, Instant lastReadAt,
        List<UUID> dependsOn) {

    public static final int MAX_TITLE = 120;
    public static final int MAX_OUTCOME = 300;
    public static final int MAX_REPORT = 2000;
    /** Description d'une sous-tâche relue dans Jira, au plus (V33). */
    public static final int MAX_DESCRIPTION = 32_000;
    /** Libellé du type d'une sous-tâche relu dans Jira, au plus (V34). */
    public static final int MAX_ISSUE_TYPE = 100;

    public SpecTask {
        Objects.requireNonNull(id);
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(basis);
        Objects.requireNonNull(origin);
        Objects.requireNonNull(creation);
        Objects.requireNonNull(status);
        title = title == null ? "" : title.strip();
        outcome = outcome == null ? "" : outcome.strip();
        if (position < 0) {
            throw invalid("La position d'une tâche est positive.");
        }
        if (title.isEmpty() || title.length() > MAX_TITLE) {
            throw invalid("Le titre d'une tâche fait de 1 à " + MAX_TITLE + " caractères.");
        }
        if (outcome.length() > MAX_OUTCOME || outcome.isEmpty() && origin != TaskOrigin.JIRA) {
            throw invalid("Le résultat attendu d'une tâche fait de 1 à " + MAX_OUTCOME + " caractères.");
        }
        if (creation == TaskCreation.CREATED && !LinkedJiraTicket.isKey(jiraKey)) {
            throw invalid("Une tâche créée dans Jira a la clé de sa sous-tâche.");
        }
        if (pendingTarget == TaskStatus.MISSING) {
            throw invalid("Aucune transition vers « introuvable ».");
        }
        report = report == null || report.isBlank() ? null : report.strip();
        if (report != null && report.length() > MAX_REPORT) {
            throw new DomainException(ErrorCode.REPORT_TOO_LONG);
        }
        pendingComment = pendingComment && report != null;
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }

    private static DomainException invalid(String message) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, message);
    }

    /** Tâche proposée ou ajoutée à la main, pas encore créée dans Jira. */
    public static SpecTask proposed(UUID id, UUID sessionId, int position, String title, String outcome, Basis basis,
            TaskOrigin origin, List<UUID> dependsOn) {
        return new SpecTask(id, sessionId, position, title, outcome, basis, origin, TaskCreation.NOT_CREATED, null,
                TaskStatus.TODO, null, null, null, false, null, null, dependsOn);
    }

    /** Sous-tâche déjà présente sous la story, reprise comme tâche (FR-006). */
    public static SpecTask fromJira(UUID id, UUID sessionId, int position, String summary, String key, TaskStatus status,
            String statusName, Instant readAt) {
        String title = summary == null || summary.isBlank() ? key
                : summary.strip().length() > MAX_TITLE ? summary.strip().substring(0, MAX_TITLE) : summary;
        return new SpecTask(id, sessionId, position, title, "", Basis.NONE, TaskOrigin.JIRA, TaskCreation.CREATED, key,
                status, statusName, null, null, false, null, readAt, List.of());
    }

    public boolean isCreated() {
        return creation == TaskCreation.CREATED;
    }

    public boolean isPending() {
        return pendingTarget != null || pendingComment;
    }

    public SpecTask created(String key) {
        return new SpecTask(id, sessionId, position, title, outcome, basis, origin, TaskCreation.CREATED, key, status,
                jiraStatusName, holder, pendingTarget, pendingComment, report, lastReadAt, dependsOn);
    }

    public SpecTask renamed(String newTitle) {
        return new SpecTask(id, sessionId, position, newTitle, outcome, basis, origin, creation, jiraKey, status,
                jiraStatusName, holder, pendingTarget, pendingComment, report, lastReadAt, dependsOn);
    }

    public SpecTask at(int newPosition) {
        return new SpecTask(id, sessionId, newPosition, title, outcome, basis, origin, creation, jiraKey, status,
                jiraStatusName, holder, pendingTarget, pendingComment, report, lastReadAt, dependsOn);
    }

    public SpecTask dependingOn(List<UUID> tasks) {
        return new SpecTask(id, sessionId, position, title, outcome, basis, origin, creation, jiraKey, status,
                jiraStatusName, holder, pendingTarget, pendingComment, report, lastReadAt, tasks);
    }

    /** Prise par un agent ; {@code pending} : Jira n'a pas pu être prévenu. */
    public SpecTask started(Holder by, boolean pending) {
        return new SpecTask(id, sessionId, position, title, outcome, basis, origin, creation, jiraKey,
                TaskStatus.IN_PROGRESS, jiraStatusName, Objects.requireNonNull(by),
                pending ? TaskStatus.IN_PROGRESS : null, false, report, lastReadAt, dependsOn);
    }

    /** Terminée par l'agent, avec un compte rendu facultatif. */
    public SpecTask completed(String completionReport, boolean pending) {
        return new SpecTask(id, sessionId, position, title, outcome, basis, origin, creation, jiraKey, TaskStatus.DONE,
                jiraStatusName, null, pending ? TaskStatus.DONE : null, pending, completionReport, lastReadAt,
                dependsOn);
    }

    /** Libérée par l'utilisateur (FR-022) : redevient à faire, sans connexion. */
    public SpecTask released(boolean pending) {
        return new SpecTask(id, sessionId, position, title, outcome, basis, origin, creation, jiraKey, TaskStatus.TODO,
                jiraStatusName, null, pending ? TaskStatus.TODO : null, pendingComment, report, lastReadAt, dependsOn);
    }

    /** Transition et commentaire en attente envoyés à Jira, ou déjà vrais dans Jira. */
    public SpecTask synced() {
        return new SpecTask(id, sessionId, position, title, outcome, basis, origin, creation, jiraKey, status,
                jiraStatusName, holder, null, false, report, lastReadAt, dependsOn);
    }

    /** Seule la transition est envoyée ; le commentaire reste à envoyer. */
    public SpecTask transitionSynced() {
        return new SpecTask(id, sessionId, position, title, outcome, basis, origin, creation, jiraKey, status,
                jiraStatusName, holder, null, pendingComment, report, lastReadAt, dependsOn);
    }

    /**
     * Statut relu dans Jira (R6) : il s'impose. Une tâche qui n'est plus en cours perd sa connexion ;
     * une tâche en cours dans Jira garde l'agent qui la tient.
     */
    public SpecTask read(TaskStatus jiraStatus, String statusName, Instant at) {
        Holder kept = jiraStatus == TaskStatus.IN_PROGRESS ? holder : null;
        return new SpecTask(id, sessionId, position, title, outcome, basis, origin, creation, jiraKey, jiraStatus,
                statusName == null ? jiraStatusName : statusName, kept, pendingTarget, pendingComment, report, at,
                dependsOn);
    }
}
