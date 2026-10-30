package dev.specwriter.application.jira;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.IssueType;
import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.jira.RequiredField;
import dev.specwriter.domain.port.IssueTracker;
import dev.specwriter.domain.port.LinkedTicketRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.port.WikiMarkup;
import dev.specwriter.domain.specification.SpecificationVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Aperçu avant tout envoi vers Jira (FR-036, FR-037, FR-047) : rien n'est créé ni modifié.
 * <ul>
 *   <li>session liée : mise à jour de la description du ticket, sans appel à Jira ;</li>
 *   <li>sinon : création, avec les types du projet (Story par défaut) et les champs obligatoires à
 *       remplir. Le consentement {@code JIRA} est alors exigé.</li>
 * </ul>
 */
public class PreviewJiraTicket {

    public enum Mode { CREATE, UPDATE }

    public record Preview(Mode mode, String projectKey, String issueKey, List<IssueType> issueTypes,
            String defaultIssueTypeId, String issueTypeId, String summary, String descriptionWiki,
            List<RequiredField> requiredFields) {
    }

    private final SpecificationRepository versions;
    private final LinkedTicketRepository tickets;
    private final WikiMarkup markup;
    private final IssueTracker tracker;
    private final JiraAccess jira;

    public PreviewJiraTicket(SpecificationRepository versions, LinkedTicketRepository tickets, WikiMarkup markup,
            IssueTracker tracker, JiraAccess jira) {
        this.versions = versions;
        this.tickets = tickets;
        this.markup = markup;
        this.tracker = tracker;
        this.jira = jira;
    }

    /** @param issueTypeId type choisi dans l'aperçu ; {@code null} pour le type par défaut */
    public Preview run(UUID sessionId, int versionNumber, String issueTypeId) {
        SpecificationVersion version = versions.find(sessionId, versionNumber)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        String summary = version.content().title();
        String wiki = markup.toWiki(version.markdown());
        Optional<LinkedJiraTicket> linked = tickets.findBySession(sessionId);
        if (linked.isPresent()) {
            return new Preview(Mode.UPDATE, null, linked.get().issueKey(), List.of(), null, null, summary, wiki, List.of());
        }
        JiraAccess.Target target = jira.require();
        List<IssueType> types = TicketForm.types(tracker, target);
        IssueType chosen = TicketForm.chosen(types, issueTypeId);
        return new Preview(Mode.CREATE, target.projectKey(), null, types, TicketForm.defaultType(types).id(), chosen.id(),
                summary, wiki, TicketForm.toFill(tracker, target, chosen.id()));
    }
}
