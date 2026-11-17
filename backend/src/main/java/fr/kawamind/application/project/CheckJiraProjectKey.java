package fr.kawamind.application.project;

import fr.kawamind.application.jira.JiraAccess;
import fr.kawamind.domain.jira.IssueTrackerException;
import fr.kawamind.domain.port.IssueTracker;
import fr.kawamind.domain.project.Project;
import java.util.Optional;

/**
 * Vérifie la clé Jira d'un projet à l'enregistrement, quand Jira est configuré et autorisé
 * (FR-007). Une clé introuvable est signalée par un avertissement, sans bloquer l'enregistrement.
 */
public class CheckJiraProjectKey {

    private final JiraAccess jira;
    private final IssueTracker tracker;

    public CheckJiraProjectKey(JiraAccess jira, IssueTracker tracker) {
        this.jira = jira;
        this.tracker = tracker;
    }

    /** L'avertissement à afficher, vide si la clé existe ou si elle ne peut pas être vérifiée. */
    public Optional<String> warning(Project project) {
        if (project.jiraProjectKey() == null || !jira.configured() || !jira.consented()) {
            return Optional.empty();
        }
        String key = project.jiraProjectKey().value();
        return jira.target().flatMap(target -> {
            try {
                tracker.project(target.endpoint(), key);
                return Optional.empty();
            } catch (IssueTrackerException e) {
                return Optional.of(switch (e.kind()) {
                    case NOT_FOUND -> "Le projet Jira " + key + " est introuvable. Vérifiez la clé.";
                    case FORBIDDEN -> "Votre compte Jira n'a pas accès au projet " + key + ".";
                    default -> "Jira n'a pas pu vérifier la clé " + key + " pour l'instant.";
                });
            }
        });
    }
}
