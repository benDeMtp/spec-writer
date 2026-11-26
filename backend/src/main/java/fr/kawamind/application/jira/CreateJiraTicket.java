package fr.kawamind.application.jira;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.jira.ConflictDetector;
import fr.kawamind.domain.jira.CreatedIssue;
import fr.kawamind.domain.jira.IssueTrackerException;
import fr.kawamind.domain.jira.IssueType;
import fr.kawamind.domain.jira.JiraIssue;
import fr.kawamind.domain.jira.LinkedJiraTicket;
import fr.kawamind.domain.jira.RequiredField;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.IssueTracker;
import fr.kawamind.domain.port.LinkedTicketRepository;
import fr.kawamind.domain.jira.IssueLinkType;
import fr.kawamind.domain.port.SessionRelationRepository;
import fr.kawamind.domain.port.SpecificationRepository;
import fr.kawamind.domain.session.SessionRelation;
import java.util.Optional;
import org.jboss.logging.Logger;
import fr.kawamind.domain.port.WikiMarkup;
import fr.kawamind.domain.specification.SpecificationVersion;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Crée le ticket unique d'une version, après confirmation (FR-036 à FR-038) : titre de la
 * spécification, description complète en wiki, sans pièce jointe. Le ticket est lié à la session.
 */
public class CreateJiraTicket {

    static final String RELATES = "Relates";
    static final SessionRelationRepository NO_RELATIONS = new SessionRelationRepository() {
        @Override
        public Optional<SessionRelation> find(UUID sessionId) {
            return Optional.empty();
        }

        @Override
        public void save(SessionRelation relation) {
            // aucune relation sans dépôt
        }
    };
    private static final Logger LOG = Logger.getLogger(CreateJiraTicket.class);

    private final IssueTracker tracker;
    private final JiraAccess jira;
    private final WikiMarkup markup;
    private final SpecificationRepository versions;
    private final LinkedTicketRepository tickets;
    private final ClockPort clock;
    private final SessionRelationRepository relations;
    /** Label de reconnaissance (024) ; sans lui (null), aucun label n'est posé. */
    private final JiraLabelling labelling;

    /** Ticket créé, et l'avertissement d'un label non posé (024), nul sinon. */
    public record Created(LinkedJiraTicket ticket, String labelWarning) {
    }

    public CreateJiraTicket(IssueTracker tracker, JiraAccess jira, WikiMarkup markup, SpecificationRepository versions,
            LinkedTicketRepository tickets, ClockPort clock) {
        this(tracker, jira, markup, versions, tickets, clock, NO_RELATIONS);
    }

    /** Avec le lien « relates to » vers un ticket fermé, créé après la story (FR-035 de 002). */
    public CreateJiraTicket(IssueTracker tracker, JiraAccess jira, WikiMarkup markup, SpecificationRepository versions,
            LinkedTicketRepository tickets, ClockPort clock, SessionRelationRepository relations) {
        this(tracker, jira, markup, versions, tickets, clock, relations, null);
    }

    public CreateJiraTicket(IssueTracker tracker, JiraAccess jira, WikiMarkup markup, SpecificationRepository versions,
            LinkedTicketRepository tickets, ClockPort clock, SessionRelationRepository relations,
            JiraLabelling labelling) {
        this.labelling = labelling;
        this.relations = relations;
        this.tracker = tracker;
        this.jira = jira;
        this.markup = markup;
        this.versions = versions;
        this.tickets = tickets;
        this.clock = clock;
    }

    /** @param extraFields saisies de l'utilisateur pour les champs obligatoires, par identifiant de champ */
    public LinkedJiraTicket run(UUID sessionId, int versionNumber, boolean confirm, String issueTypeId,
            Map<String, String> extraFields) {
        return create(sessionId, versionNumber, confirm, issueTypeId, extraFields).ticket();
    }

    /** Comme {@link #run}, avec l'avertissement d'un label non posé (024). */
    public Created create(UUID sessionId, int versionNumber, boolean confirm, String issueTypeId,
            Map<String, String> extraFields) {
        if (!confirm) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "La création du ticket doit être confirmée.");
        }
        JiraAccess.Target target = jira.require(sessionId);
        SpecificationVersion version = versions.find(sessionId, versionNumber)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (tickets.findBySession(sessionId).isPresent()) {
            throw new DomainException(ErrorCode.ALREADY_LINKED);
        }
        IssueType type = TicketForm.chosen(TicketForm.types(tracker, target), issueTypeId);
        Map<String, Object> values = new LinkedHashMap<>();
        for (RequiredField field : TicketForm.toFill(tracker, target, type.id())) {
            values.put(field.id(), field.toJiraValue(extraFields == null ? null : extraFields.get(field.id())));
        }

        String wiki = markup.toWiki(version.markdown());
        UUID projectId = labelling == null ? null : jira.projectOf(sessionId);
        Map<String, Object> labelled = new LinkedHashMap<>(values);
        boolean withLabel = projectId != null && labelling.label(projectId).isPresent();
        if (withLabel) {
            labelled.put("labels", labelling.forCreation(projectId,
                    values.get("labels") instanceof java.util.Collection<?> c ? c : null));
        }
        CreatedIssue created;
        String labelWarning = null;
        try {
            created = tracker.createIssue(target.endpoint(), target.requireProject(), type.id(), version.content().title(), wiki,
                    labelled);
            if (withLabel) {
                labelling.created(projectId, created.key());
            }
        } catch (IssueTrackerException e) {
            if (!withLabel || !JiraLabelling.refusedLabels(e)) {
                throw e.toDomain();
            }
            // 024 (R3) : le champ des labels n'est pas sur l'écran de création ; le label est ajouté ensuite
            try {
                created = tracker.createIssue(target.endpoint(), target.requireProject(), type.id(),
                        version.content().title(), wiki, values);
            } catch (IssueTrackerException again) {
                throw again.toDomain();
            }
            labelWarning = labelling.tag(projectId, target.endpoint(), created.key(), null);
        }
        Instant now = clock.now();
        JiraIssue written = reread(target, created.key());
        LinkedJiraTicket ticket = new LinkedJiraTicket(sessionId, created.key(), target.issueUrl(created.key()), type.name(),
                versionNumber, written == null ? now : written.updated(),
                ConflictDetector.hash(written == null ? wiki : written.description()), now, now,
                LinkedJiraTicket.SyncState.IN_SYNC);
        tickets.save(ticket);
        String storyKey = created.key();
        relations.find(sessionId).filter(r -> r.link() != SessionRelation.Link.LINKED)
                .ifPresent(r -> link(tracker, relations, target, storyKey, r));
        return new Created(ticket, labelWarning);
    }

    /**
     * Lien de la story vers le ticket fermé, du type dont le sens sortant est « relates to ». Un
     * échec laisse la story créée ; le lien peut être réessayé.
     */
    static SessionRelation link(IssueTracker tracker, SessionRelationRepository relations, JiraAccess.Target target,
            String storyKey, SessionRelation relation) {
        SessionRelation result;
        try {
            String type = tracker.linkTypes(target.endpoint()).stream().filter(IssueLinkType::relates).findFirst()
                    .map(IssueLinkType::name).orElse(RELATES);
            tracker.createLink(target.endpoint(), type, storyKey, relation.relatesTo());
            result = relation.with(SessionRelation.Link.LINKED);
        } catch (IssueTrackerException e) {
            LOG.warnf("Lien « relates to » de %s vers %s non créé (%s)", storyKey, relation.relatesTo(), e.kind());
            result = relation.with(SessionRelation.Link.FAILED);
        }
        relations.save(result);
        return result;
    }

    /**
     * Ce que Jira a enregistré sert de référence pour détecter un conflit plus tard. S'il ne répond
     * pas, le ticket existe déjà : on le lie quand même, avec ce qui a été envoyé.
     */
    private JiraIssue reread(JiraAccess.Target target, String key) {
        try {
            return tracker.getIssue(target.endpoint(), key);
        } catch (IssueTrackerException e) {
            return null;
        }
    }
}
