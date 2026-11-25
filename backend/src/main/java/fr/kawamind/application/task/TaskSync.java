package fr.kawamind.application.task;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.jira.IssueTrackerException;
import fr.kawamind.domain.jira.JiraEndpoint;
import fr.kawamind.domain.jira.TrackedIssue;
import fr.kawamind.domain.port.IssueTracker;
import fr.kawamind.domain.session.Implementation;
import fr.kawamind.domain.session.ImplementationStatus;
import fr.kawamind.domain.task.SpecTask;
import fr.kawamind.domain.task.StatusCategory;
import fr.kawamind.domain.task.TaskProgress;
import fr.kawamind.domain.task.TaskStatus;
import fr.kawamind.domain.task.TaskTracking;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Accord des tâches avec Jira (015, R5, R6, R7) : les transitions « à synchroniser » sont rejouées
 * dans l'ordre des tâches, puis les statuts sont relus et s'imposent, et le statut de la
 * spécification suit les tâches si le suivi est actif. Jira injoignable n'est pas une erreur : les
 * derniers statuts connus restent, et l'appelant le signale. À appeler sous le verrou de la session.
 */
public class TaskSync {

    private static final Logger LOG = Logger.getLogger(TaskSync.class);
    /** Préfixe d'un compte rendu dont l'agent n'est plus connu (rejeu après une coupure). */
    static final String REPORT_PREFIX = "Compte rendu de l'agent de code : ";

    private final TaskDesk desk;
    private final TaskJira jira;

    public TaskSync(TaskDesk desk, TaskJira jira) {
        this.desk = desk;
        this.jira = jira;
    }

    /** Rejoue puis relit ; vrai si Jira n'a pas pu être joint. Sans tâche créée, aucun appel. */
    public boolean refresh(UUID sessionId) {
        List<SpecTask> created = desk.tasks.tasks(sessionId).stream().filter(SpecTask::isCreated).toList();
        if (created.isEmpty()) {
            return false;
        }
        JiraEndpoint endpoint;
        try {
            endpoint = desk.endpoint(sessionId);
        } catch (DomainException e) {
            // Jira n'est plus configuré ou plus consenti : les statuts connus restent
            return true;
        }
        boolean unreachable = replay(sessionId, endpoint) || reread(sessionId, endpoint);
        applyStatus(sessionId);
        return unreachable;
    }

    /** Transitions et commentaires en attente ; vrai si Jira est injoignable. Un refus lève l'attente. */
    boolean replay(UUID sessionId, JiraEndpoint endpoint) {
        for (SpecTask task : desk.tasks.tasks(sessionId)) {
            if (!task.isPending()) {
                continue;
            }
            SpecTask current = task;
            if (current.pendingTarget() != null) {
                try {
                    if (jira.moveTo(endpoint, current.jiraKey(), StatusCategory.of(current.pendingTarget()))
                            == TaskJira.Result.UNREACHABLE) {
                        return true;
                    }
                } catch (DomainException refused) {
                    LOG.warnf("Transition en attente de %s refusée par Jira (%s) : le statut de Jira s'impose",
                            current.jiraKey(), refused.code());
                }
                current = current.transitionSynced();
                desk.tasks.update(current);
            }
            if (current.pendingComment()) {
                try {
                    if (jira.comment(endpoint, current.jiraKey(), desk.markup.toWiki(REPORT_PREFIX + current.report()))
                            == TaskJira.Result.UNREACHABLE) {
                        return true;
                    }
                } catch (DomainException refused) {
                    LOG.warnf("Compte rendu en attente de %s refusé par Jira (%s)", current.jiraKey(), refused.code());
                }
                desk.tasks.update(current.synced());
            }
        }
        return false;
    }

    /** Relit les statuts par paquets de 50 clés ; une clé absente devient introuvable (R6). */
    boolean reread(UUID sessionId, JiraEndpoint endpoint) {
        List<SpecTask> created = desk.tasks.tasks(sessionId).stream().filter(SpecTask::isCreated).toList();
        Map<String, TrackedIssue> found = new HashMap<>();
        try {
            for (int i = 0; i < created.size(); i += IssueTracker.KEYS_PER_READ) {
                List<String> keys = created.subList(i, Math.min(created.size(), i + IssueTracker.KEYS_PER_READ))
                        .stream().map(SpecTask::jiraKey).toList();
                desk.tracker.issuesByKeys(endpoint, TaskDesk.project(keys.get(0)), keys)
                        .forEach(issue -> found.put(issue.key(), issue));
            }
        } catch (IssueTrackerException e) {
            if (TaskJira.unreachable(e)) {
                return true;
            }
            LOG.warnf("Relecture des sous-tâches refusée par Jira (%s)", e.kind());
            return true;
        }
        Map<UUID, String> descriptions = desk.tasks.descriptions(sessionId);
        Map<UUID, String> types = desk.tasks.issueTypes(sessionId);
        for (SpecTask task : created) {
            TrackedIssue issue = found.get(task.jiraKey());
            String description = issue == null ? descriptions.get(task.id()) : description(issue);
            if (!Objects.equals(description, descriptions.get(task.id()))) {
                desk.tasks.describe(task.id(), description);
            }
            String type = issue == null ? types.get(task.id()) : issueType(issue);
            if (!Objects.equals(type, types.get(task.id()))) {
                desk.tasks.type(task.id(), type);
            }
            SpecTask read;
            if (issue == null) {
                read = task.read(TaskStatus.MISSING, null, desk.clock.now());
            } else {
                Optional<StatusCategory> category = StatusCategory.fromKey(issue.statusCategory());
                read = task.read(category.map(StatusCategory::status).orElse(task.status()), issue.status(),
                        desk.clock.now());
            }
            if (!read.equals(task)) {
                desk.tasks.update(read);
            }
        }
        return false;
    }

    /** Description en Markdown, nulle si vide, coupée à {@link SpecTask#MAX_DESCRIPTION}. */
    static String description(TrackedIssue issue) {
        String text = issue.description() == null ? "" : issue.description().strip();
        if (text.isEmpty()) {
            return null;
        }
        String cut = "\n\n[description tronquée]";
        return text.length() <= SpecTask.MAX_DESCRIPTION ? text
                : text.substring(0, SpecTask.MAX_DESCRIPTION - cut.length()) + cut;
    }

    /** Libellé du type dans Jira (022), nul si vide, coupé à {@link SpecTask#MAX_ISSUE_TYPE}. */
    static String issueType(TrackedIssue issue) {
        String type = issue.issueType() == null ? "" : issue.issueType().strip();
        if (type.isEmpty()) {
            return null;
        }
        return type.length() <= SpecTask.MAX_ISSUE_TYPE ? type : type.substring(0, SpecTask.MAX_ISSUE_TYPE);
    }

    /** Le statut de la spécification suit les tâches, sauf suivi suspendu ou brouillon (R7). */
    public void applyStatus(UUID sessionId) {
        if (desk.tasks.tracking(sessionId) == TaskTracking.SUSPENDED) {
            return;
        }
        Optional<ImplementationStatus> computed = TaskProgress.status(desk.tasks.tasks(sessionId));
        Optional<Implementation> current = desk.sessions.implementation(sessionId);
        if (computed.isEmpty() || current.isEmpty() || current.get().status() == ImplementationStatus.DRAFT) {
            return;
        }
        Implementation next = current.get().change(computed.get(), ImplementationStatus.Actor.TASKS, true,
                desk.clock.now());
        if (!next.equals(current.get())) {
            desk.sessions.saveImplementation(sessionId, next);
        }
    }
}
