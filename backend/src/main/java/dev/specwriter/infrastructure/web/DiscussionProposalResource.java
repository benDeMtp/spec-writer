package dev.specwriter.infrastructure.web;

import dev.specwriter.application.conversation.ProposeRevision;
import dev.specwriter.domain.conversation.ConversationMessage;
import dev.specwriter.domain.port.ConversationLlm.ChangeCandidate;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * « Proposer une nouvelle version » (011, US2, contracts/api.md) : l'agent relève d'abord les
 * changements que la discussion avance, la personne coche ceux à retenir, puis la rédaction rend 202
 * et le tour, dont le flux ({@code /api/conversations/{cid}/turns/{turnId}/events}) suit la
 * rédaction jusqu'à la proposition.
 */
@Path("/api/conversations/{cid}/proposals")
@Produces(MediaType.APPLICATION_JSON)
public class DiscussionProposalResource {

    /** {@code changes} : changements retenus ; absent ou vide, la discussion seule fait foi. */
    public record ProposeInput(List<String> changes) {
    }

    /** {@code message} : le message des changements retenus, ajouté à la discussion. */
    public record Started(String turnId, ConversationMessage message) {
    }

    public record Candidates(List<ChangeCandidate> changes) {
    }

    @Inject
    ProposeRevision proposeRevision;

    @POST
    public Response propose(@PathParam("cid") String cid, ProposeInput input) {
        ProposeRevision.Started started = proposeRevision.propose(Ids.parse(cid),
                input == null || input.changes() == null ? List.of() : input.changes());
        return Response.accepted(new Started(started.turnId().toString(), started.message())).build();
    }

    @POST
    @Path("/candidates")
    public Candidates candidates(@PathParam("cid") String cid) {
        return new Candidates(proposeRevision.changes(Ids.parse(cid)));
    }
}
