package dev.specwriter.infrastructure.web;

import dev.specwriter.application.knowledge.CompareWithIssue;
import dev.specwriter.application.knowledge.RelatedIssues;
import dev.specwriter.application.knowledge.RelatedIssuesStore;
import dev.specwriter.application.session.ResolveRelatedIssue;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.llm.IssueComparison;
import dev.specwriter.domain.port.SessionRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

/**
 * Tickets proches d'un besoin (contracts/openapi.yaml de 002, FR-034, FR-035) : liste, comparaison
 * avec un ticket non fermé, et choix de l'utilisateur, avant les questions.
 */
@Path("/api/sessions/{id}/related-issues")
@Produces(MediaType.APPLICATION_JSON)
public class RelatedIssuesResource {

    @Inject
    SourceLinks sourceLinks;

    public record DecisionInput(String decision, String key) {
    }

    public record ComparisonView(String key, List<String> common, List<String> different, String suggestion) {
    }

    /** {@code proposal} : pour SAME, la modification assistée proposée sur le ticket importé. */
    public record DecisionView(UUID sessionId, String relatesTo, SessionViews.ProposalView proposal) {
    }

    @Inject
    RelatedIssuesStore store;
    @Inject
    SessionRepository sessions;
    @Inject
    CompareWithIssue compare;
    @Inject
    ResolveRelatedIssue resolve;

    @GET
    public RelatedIssues related(@PathParam("id") String id) {
        UUID sessionId = Ids.parse(id);
        if (sessions.find(sessionId).isEmpty()) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
        return store.find(sessionId).orElse(RelatedIssues.UNAVAILABLE);
    }

    @POST
    @Path("/{key}/comparison")
    public ComparisonView comparison(@PathParam("id") String id, @PathParam("key") String key) {
        IssueComparison c = compare.run(Ids.parse(id), key);
        return new ComparisonView(key, c.common(), c.different(), c.suggestion().name());
    }

    @PUT
    @Path("/decision")
    @Consumes(MediaType.APPLICATION_JSON)
    public DecisionView decide(@PathParam("id") String id, DecisionInput input) {
        DecisionInput in = input == null ? new DecisionInput(null, null) : input;
        ResolveRelatedIssue.Outcome outcome = resolve.run(Ids.parse(id), decision(in.decision()), in.key());
        return new DecisionView(outcome.sessionId(), outcome.relatesTo(),
                outcome.proposal() == null ? null : sourceLinks.complete(SessionViews.proposal(outcome.proposal())));
    }

    private static RelatedIssues.Decision decision(String raw) {
        try {
            return RelatedIssues.Decision.valueOf(raw == null ? "" : raw);
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Choisissez LINK_NEW, SAME, DIFFERENT ou NONE.");
        }
    }
}
