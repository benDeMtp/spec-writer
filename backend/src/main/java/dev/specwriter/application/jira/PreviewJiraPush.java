package dev.specwriter.application.jira;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.port.LinkedTicketRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.port.WikiMarkup;
import dev.specwriter.domain.specification.SpecificationVersion;
import java.util.UUID;

/**
 * Aperçu de l'envoi d'une version vers le ticket lié (FR-047) : la description telle que Jira la
 * recevra. Rien n'est envoyé. Le mode création arrive avec la user story 5.
 */
public class PreviewJiraPush {

    public enum Mode { CREATE, UPDATE }

    public record Preview(Mode mode, String issueKey, String summary, String descriptionWiki) {
    }

    private final SpecificationRepository versions;
    private final LinkedTicketRepository tickets;
    private final WikiMarkup markup;

    public PreviewJiraPush(SpecificationRepository versions, LinkedTicketRepository tickets, WikiMarkup markup) {
        this.versions = versions;
        this.tickets = tickets;
        this.markup = markup;
    }

    public Preview run(UUID sessionId, int versionNumber) {
        SpecificationVersion version = versions.find(sessionId, versionNumber)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        LinkedJiraTicket ticket = tickets.findBySession(sessionId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Cette session n'est liée à aucun ticket."));
        return new Preview(Mode.UPDATE, ticket.issueKey(), version.content().title(), markup.toWiki(version.markdown()));
    }
}
