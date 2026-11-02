package dev.specwriter.domain.port;

import dev.specwriter.domain.jira.CreatedIssue;
import dev.specwriter.domain.jira.IssueType;
import dev.specwriter.domain.jira.JiraEndpoint;
import dev.specwriter.domain.jira.JiraIssue;
import dev.specwriter.domain.jira.JiraSearchPage;
import dev.specwriter.domain.jira.RequiredField;
import java.util.List;
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

    /** Types de ticket créables dans le projet, sous-tâches exclues ({@code createmeta/{project}/issuetypes}). */
    List<IssueType> issueTypes(JiraEndpoint endpoint, String projectKey);

    /** Champs obligatoires sans valeur par défaut d'un type de ticket ({@code createmeta/{project}/issuetypes/{id}}). */
    List<RequiredField> requiredFields(JiraEndpoint endpoint, String projectKey, String issueTypeId);

    /**
     * Crée un ticket. {@code extraFields} contient des valeurs déjà au format de Jira (voir
     * {@link RequiredField#toJiraValue(String)}).
     */
    CreatedIssue createIssue(JiraEndpoint endpoint, String projectKey, String issueTypeId, String summary,
            String wikiDescription, Map<String, Object> extraFields);
}
