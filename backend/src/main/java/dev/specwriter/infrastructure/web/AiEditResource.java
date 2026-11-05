package dev.specwriter.infrastructure.web;

import dev.specwriter.application.specification.AcceptAiEdit;
import dev.specwriter.application.specification.ProposeAiEdit;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** {@code /api/sessions/{id}/ai-edits} : modification proposée par l'IA, puis appliquée sur validation (FR-044). */
@Path("/api/sessions/{id}/ai-edits")
@Produces(MediaType.APPLICATION_JSON)
public class AiEditResource {

    @Inject
    SourceLinks sourceLinks;

    public record EditRequest(String instruction) {
    }

    @Inject
    ProposeAiEdit proposeAiEdit;
    @Inject
    AcceptAiEdit acceptAiEdit;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public SessionViews.ProposalView propose(@PathParam("id") String id, EditRequest request) {
        return sourceLinks.complete(SessionViews.proposal(proposeAiEdit.run(Ids.parse(id),
                request == null ? null : request.instruction())));
    }

    @POST
    @Path("/{proposalId}/accept")
    public Response accept(@PathParam("id") String id, @PathParam("proposalId") String proposalId) {
        return Response.status(Response.Status.CREATED)
                .entity(sourceLinks.complete(SessionViews.specification(acceptAiEdit.run(Ids.parse(id),
                        Ids.parse(proposalId))))).build();
    }
}
