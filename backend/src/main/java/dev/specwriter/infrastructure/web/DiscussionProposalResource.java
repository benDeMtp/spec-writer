package dev.specwriter.infrastructure.web;

import dev.specwriter.application.conversation.ProposeRevision;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * « Proposer une nouvelle version » (011, US2, contracts/api.md) : 202 et le tour, dont le flux
 * ({@code /api/conversations/{cid}/turns/{turnId}/events}) suit la rédaction jusqu'à la proposition.
 */
@Path("/api/conversations/{cid}/proposals")
@Produces(MediaType.APPLICATION_JSON)
public class DiscussionProposalResource {

    public record Started(String turnId) {
    }

    @Inject
    ProposeRevision proposeRevision;

    @POST
    public Response propose(@PathParam("cid") String cid) {
        ProposeRevision.Started started = proposeRevision.propose(Ids.parse(cid));
        return Response.accepted(new Started(started.turnId().toString())).build();
    }
}
