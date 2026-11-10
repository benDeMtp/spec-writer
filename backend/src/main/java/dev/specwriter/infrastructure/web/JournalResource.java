package dev.specwriter.infrastructure.web;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.infrastructure.logging.JournalEntry;
import dev.specwriter.infrastructure.logging.JournalSetup;
import dev.specwriter.infrastructure.logging.LogJournal;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * {@code GET /api/journal} : les 500 dernières entrées WARN et ERROR, expurgées (FR-031, research.md
 * R15) ; {@code DELETE /api/journal} : vide le journal.
 */
@Path("/api/journal")
@Produces(MediaType.APPLICATION_JSON)
public class JournalResource {

    private static final Logger LOG = Logger.getLogger(JournalResource.class);

    @Inject
    LogJournal journal;

    @Inject
    JournalSetup setup;

    @GET
    public List<JournalEntry> latest(@QueryParam("level") String level) {
        if (level != null && !level.isBlank() && !"WARN".equals(level) && !"ERROR".equals(level)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Choisissez le niveau WARN ou ERROR.");
        }
        return journal.latest(level == null || level.isBlank() ? null : level);
    }

    @DELETE
    public void purge() {
        setup.purge();
        LOG.info("Journal des erreurs vidé");
    }
}
