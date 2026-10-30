package dev.specwriter.domain.port;

import dev.specwriter.domain.jira.JiraEndpoint;
import java.util.Map;
import java.util.Set;

/**
 * Port du gestionnaire de tickets (Jira Server / Data Center, research.md R10). Les opérations
 * lèvent {@link dev.specwriter.domain.jira.IssueTrackerException}. Complété par US6 et US5.
 */
public interface IssueTracker {

    String PERM_BROWSE = "BROWSE_PROJECTS";
    String PERM_CREATE = "CREATE_ISSUES";
    String PERM_EDIT = "EDIT_ISSUES";

    /** Version du serveur ({@code serverInfo}). */
    String serverVersion(JiraEndpoint endpoint);

    /** Login du propriétaire du jeton ({@code myself}). */
    String currentUser(JiraEndpoint endpoint);

    /** Vérifie que le projet existe et est visible ({@code project/{key}}). */
    void project(JiraEndpoint endpoint, String projectKey);

    /** Droits accordés sur le projet ({@code mypermissions}). */
    Map<String, Boolean> permissions(JiraEndpoint endpoint, String projectKey, Set<String> permissions);
}
