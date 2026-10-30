package dev.specwriter.domain.port;

import dev.specwriter.domain.jira.JiraEndpoint;
import dev.specwriter.domain.jira.JiraIssue;
import dev.specwriter.domain.jira.JiraSearchPage;
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

    /**
     * Recherche plein texte dans un projet ({@code search}, champs {@code summary,status,issuetype,updated}).
     * La requête JQL est construite par l'adaptateur : {@code terms} n'est jamais interprété.
     */
    JiraSearchPage searchText(JiraEndpoint endpoint, String projectKey, String terms, int startAt, int maxResults);

    /** Lecture d'un ticket ({@code issue/{key}}, champs {@code summary,description,updated}). */
    JiraIssue getIssue(JiraEndpoint endpoint, String issueKey);

    /** Remplace la description du ticket, et rien d'autre. {@code wikiDescription} est en wiki markup. */
    void updateDescription(JiraEndpoint endpoint, String issueKey, String wikiDescription);
}
