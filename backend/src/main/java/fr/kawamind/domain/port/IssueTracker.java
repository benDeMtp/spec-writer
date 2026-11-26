package fr.kawamind.domain.port;

import fr.kawamind.domain.jira.CreatedIssue;
import fr.kawamind.domain.jira.IssueLinkType;
import fr.kawamind.domain.jira.IssueStatus;
import fr.kawamind.domain.jira.IssueType;
import fr.kawamind.domain.jira.JiraEndpoint;
import fr.kawamind.domain.jira.JiraIssue;
import fr.kawamind.domain.jira.JiraSearchPage;
import fr.kawamind.domain.jira.ParentIssue;
import fr.kawamind.domain.jira.RequiredField;
import fr.kawamind.domain.jira.TrackedIssue;
import fr.kawamind.domain.jira.TrackedIssuePage;
import fr.kawamind.domain.jira.Transition;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Port du gestionnaire de tickets (Jira Server / Data Center, research.md R10). Les opérations
 * lèvent {@link fr.kawamind.domain.jira.IssueTrackerException}. Complété par US6 et US5.
 */
public interface IssueTracker {

    String PERM_BROWSE = "BROWSE_PROJECTS";
    String PERM_CREATE = "CREATE_ISSUES";
    String PERM_EDIT = "EDIT_ISSUES";
    /** Clés lues au plus par {@link #issuesByKeys}. */
    int KEYS_PER_READ = 50;

    /** Version du serveur ({@code serverInfo}). */
    String serverVersion(JiraEndpoint endpoint);

    /** Login du propriétaire du jeton ({@code myself}). */
    String currentUser(JiraEndpoint endpoint);

    /** Vérifie que le projet existe et est visible ({@code project/{key}}). */
    void project(JiraEndpoint endpoint, String projectKey);

    /** Droits accordés sur le projet ({@code mypermissions}). */
    Map<String, Boolean> permissions(JiraEndpoint endpoint, String projectKey, Set<String> permissions);

    /**
     * Recherche plein texte dans un projet, du plus récemment modifié au plus ancien ({@code search},
     * champs {@code summary,status,issuetype,updated}). La requête JQL est construite par
     * l'adaptateur : {@code terms} n'est jamais interprété.
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

    /**
     * Tickets du projet modifiés depuis {@code since} (tous si nul), du plus ancien au plus récent,
     * pour l'index de connaissance (research.md R20). La description est convertie en Markdown.
     */
    TrackedIssuePage searchUpdatedSince(JiraEndpoint endpoint, String projectKey, Instant since, int startAt,
            int maxResults);

    /**
     * Tickets du projet ouverts (catégorie de statut autre que {@code done}) ou modifiés depuis
     * {@code since}, non nul, du plus ancien au plus récent : indexation complète qui garde les
     * tickets fermés sans les relire (FR-002 de 003).
     */
    TrackedIssuePage searchOpenOrUpdatedSince(JiraEndpoint endpoint, String projectKey, Instant since, int startAt,
            int maxResults);

    /**
     * Tickets du projet lus par clé ({@link #KEYS_PER_READ} au plus, au format {@code [A-Z][A-Z0-9_]+-\d+},
     * sinon {@link IllegalArgumentException}) ; une clé inexistante est ignorée.
     */
    List<TrackedIssue> issuesByKeys(JiraEndpoint endpoint, String projectKey, List<String> keys);

    /** Clés de tous les tickets du projet ({@code fields=key}), pour retirer les tickets supprimés. */
    List<String> listKeys(JiraEndpoint endpoint, String projectKey);

    /** Statut à jour d'un ticket ({@code fields=status,updated}) ; {@code NOT_FOUND} s'il a été supprimé. */
    IssueStatus issueStatus(JiraEndpoint endpoint, String issueKey);

    /** Types de liens entre tickets ({@code issueLinkType}). */
    List<IssueLinkType> linkTypes(JiraEndpoint endpoint);

    /** Crée un lien {@code outwardKey} → {@code inwardKey} du type nommé ({@code issueLink}, FR-035). */
    void createLink(JiraEndpoint endpoint, String typeName, String outwardKey, String inwardKey);

    // Tâches d'une spécification (015, research R2) : jamais de suppression dans Jira (FR-008).

    /** Premier type sous-tâche du projet, dans l'ordre de Jira ({@code createmeta/{project}/issuetypes}). */
    Optional<IssueType> subtaskType(JiraEndpoint endpoint, String projectKey);

    /** La story et ses sous-tâches ({@code issue/{key}}, champs {@code summary,status,issuetype,subtasks}). */
    ParentIssue parentIssue(JiraEndpoint endpoint, String issueKey);

    /** Crée une sous-tâche de {@code parentKey} ({@code POST issue} avec {@code fields.parent.key}). */
    default CreatedIssue createSubtask(JiraEndpoint endpoint, String projectKey, String parentKey, String issueTypeId,
            String summary, String wikiDescription) {
        return createSubtask(endpoint, projectKey, parentKey, issueTypeId, summary, wikiDescription, List.of());
    }

    /** Crée une sous-tâche avec ces labels ({@code fields.labels}, 024). */
    CreatedIssue createSubtask(JiraEndpoint endpoint, String projectKey, String parentKey, String issueTypeId,
            String summary, String wikiDescription, List<String> labels);

    /** Ajoute un label sans toucher aux autres ({@code PUT issue/{key}}, {@code update.labels.add}, 024). */
    void addLabel(JiraEndpoint endpoint, String issueKey, String label);

    /** Remplace le résumé d'un ticket, et rien d'autre. */
    void updateSummary(JiraEndpoint endpoint, String issueKey, String summary);

    /** Transitions offertes par le workflow, dans l'ordre de Jira ({@code issue/{key}/transitions}). */
    List<Transition> transitions(JiraEndpoint endpoint, String issueKey);

    /** Applique une transition ({@code POST issue/{key}/transitions}). */
    void transition(JiraEndpoint endpoint, String issueKey, String transitionId);

    /** Affecte le ticket à un utilisateur, désigné par son login ({@code PUT issue/{key}/assignee}, 023). */
    void assign(JiraEndpoint endpoint, String issueKey, String login);

    /** Ajoute un commentaire en wiki markup ({@code POST issue/{key}/comment}). */
    void addComment(JiraEndpoint endpoint, String issueKey, String wikiBody);
}
