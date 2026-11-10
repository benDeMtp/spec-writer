package dev.specwriter.infrastructure.web;

import dev.specwriter.application.specification.ProposalStore;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Proposition d'une session en attente de relecture (011, US2, contracts/api.md) : relue, puis
 * appliquée ({@code …/ai-edits/{proposalId}/accept}) ou abandonnée. Elle naît d'une discussion
 * ({@link DiscussionProposalResource}).
 */
@Path("/api/sessions/{id}/proposals")
@Produces(MediaType.APPLICATION_JSON)
public class ProposalResource {

    @Inject
    ProposalStore proposals;
    @Inject
    SourceLinks sourceLinks;

    @GET
    @Path("/{proposalId}")
    public SessionViews.ProposalView get(@PathParam("id") String id, @PathParam("proposalId") String proposalId) {
        return proposals.find(Ids.parse(id), Ids.parse(proposalId)).map(SessionViews::proposal)
                .map(sourceLinks::complete)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Cette proposition n'existe plus."));
    }

    /** Abandon : rien n'est créé (FR-010). Une proposition déjà expirée ou prise ne change rien. */
    @DELETE
    @Path("/{proposalId}")
    public Response discard(@PathParam("id") String id, @PathParam("proposalId") String proposalId) {
        proposals.discard(Ids.parse(id), Ids.parse(proposalId));
        return Response.noContent().build();
    }
}
