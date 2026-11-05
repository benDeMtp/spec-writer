package dev.specwriter.application.jira;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.ConflictDetector;
import dev.specwriter.domain.jira.CreatedIssue;
import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.IssueType;
import dev.specwriter.domain.jira.JiraIssue;
import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.jira.RequiredField;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.IssueTracker;
import dev.specwriter.domain.port.LinkedTicketRepository;
import dev.specwriter.domain.jira.IssueLinkType;
import dev.specwriter.domain.port.SessionRelationRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.session.SessionRelation;
import java.util.Optional;
import org.jboss.logging.Logger;
import dev.specwriter.domain.port.WikiMarkup;
import dev.specwriter.domain.specification.SpecificationVersion;
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

    public CreateJiraTicket(IssueTracker tracker, JiraAccess jira, WikiMarkup markup, SpecificationRepository versions,
            LinkedTicketRepository tickets, ClockPort clock) {
        this(tracker, jira, markup, versions, tickets, clock, NO_RELATIONS);
    }

    /** Avec le lien « relates to » vers un ticket fermé, créé après la story (FR-035 de 002). */
    public CreateJiraTicket(IssueTracker tracker, JiraAccess jira, WikiMarkup markup, SpecificationRepository versions,
            LinkedTicketRepository tickets, ClockPort clock, SessionRelationRepository relations) {
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
        CreatedIssue created;
        try {
            created = tracker.createIssue(target.endpoint(), target.projectKey(), type.id(), version.content().title(), wiki,
                    values);
        } catch (IssueTrackerException e) {
            throw e.toDomain();
        }
        Instant now = clock.now();
        JiraIssue written = reread(target, created.key());
        LinkedJiraTicket ticket = new LinkedJiraTicket(sessionId, created.key(), target.issueUrl(created.key()), type.name(),
                versionNumber, written == null ? now : written.updated(),
                ConflictDetector.hash(written == null ? wiki : written.description()), now, now,
                LinkedJiraTicket.SyncState.IN_SYNC);
        tickets.save(ticket);
        relations.find(sessionId).filter(r -> r.link() != SessionRelation.Link.LINKED)
                .ifPresent(r -> link(tracker, relations, target, created.key(), r));
        return ticket;
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
