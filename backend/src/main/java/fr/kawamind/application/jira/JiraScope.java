package fr.kawamind.application.jira;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.jira.JiraLabel;
import fr.kawamind.domain.jira.LinkedJiraTicket;
import fr.kawamind.domain.port.JiraLabelRepository;
import fr.kawamind.domain.port.LinkedTicketRepository;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.port.TicketLabels;
import java.util.Optional;
import java.util.UUID;

/**
 * Périmètre des agents de code (024, US3, research R5) : sur un projet qui a un label de
 * reconnaissance, seules les features dont la story le porte, et les tâches dont la sous-tâche le
 * porte, sont servies. Sans label, sans ticket lié, ou sans ce service ({@link #OPEN}), tout l'est.
 */
public class JiraScope {

    /** Aucun périmètre : comportement d'avant la 024. */
    public static final JiraScope OPEN = new JiraScope(null, null, null, null);

    private final JiraLabelRepository labels;
    private final TicketLabels tickets;
    private final LinkedTicketRepository linked;
    private final SessionRepository sessions;

    public JiraScope(JiraLabelRepository labels, TicketLabels tickets, LinkedTicketRepository linked,
            SessionRepository sessions) {
        this.labels = labels;
        this.tickets = tickets;
        this.linked = linked;
        this.sessions = sessions;
    }

    private Optional<String> label(UUID projectId) {
        return labels == null || projectId == null ? Optional.empty() : labels.find(projectId).map(JiraLabel::value);
    }

    private UUID projectOf(UUID featureId) {
        return sessions == null ? null : sessions.find(featureId).map(s -> s.projectId()).orElse(null);
    }

    /** La feature est servie aux agents. */
    public boolean feature(UUID featureId) {
        return outOfScope(featureId).isEmpty();
    }

    /** Refus d'une feature hors périmètre, avec le ticket et le label manquant. */
    public void requireFeature(UUID featureId) {
        Optional<String> refusal = outOfScope(featureId);
        if (refusal.isPresent()) {
            throw new DomainException(ErrorCode.NOT_IN_JIRA_SCOPE, refusal.get());
        }
    }

    private Optional<String> outOfScope(UUID featureId) {
        UUID projectId = projectOf(featureId);
        Optional<String> label = label(projectId);
        if (label.isEmpty()) {
            return Optional.empty();
        }
        Optional<LinkedJiraTicket> ticket = linked.findBySession(featureId);
        if (ticket.isEmpty() || tickets.has(projectId, ticket.get().issueKey(), label.get())) {
            return Optional.empty();
        }
        return Optional.of("La feature " + featureId + " est liée à " + ticket.get().issueKey() + ", qui ne porte pas le "
                + "label " + label.get() + " : elle n'est pas suivie par spec-writer dans Jira.");
    }

    /** La sous-tâche est servie aux agents (projet de la feature). */
    public boolean task(UUID featureId, String jiraKey) {
        if (jiraKey == null) {
            return true;
        }
        UUID projectId = projectOf(featureId);
        return label(projectId).map(l -> tickets.has(projectId, jiraKey, l)).orElse(true);
    }

    /** Refus d'une tâche hors périmètre. */
    public void requireTask(UUID featureId, String jiraKey) {
        if (!task(featureId, jiraKey)) {
            UUID projectId = projectOf(featureId);
            throw new DomainException(ErrorCode.NOT_IN_JIRA_SCOPE, "La sous-tâche " + jiraKey + " ne porte pas le label "
                    + label(projectId).orElse("") + " : elle n'est pas suivie par spec-writer.");
        }
    }
}
