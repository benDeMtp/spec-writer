package fr.kawamind.infrastructure.web;

import fr.kawamind.application.specification.AcceptAiEdit;
import fr.kawamind.domain.port.SpecificationRepository;
import fr.kawamind.domain.specification.SpecificationVersion;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * {@code /api/sessions/{id}/ai-edits/{proposalId}/accept} : une proposition de l'IA (discussion,
 * fusion d'un conflit Jira, « C'est la même ») appliquée sur validation (FR-044). La consigne en un
 * coup est retirée (011) : une nouvelle version se propose depuis la discussion.
 */
@Path("/api/sessions/{id}/ai-edits")
@Produces(MediaType.APPLICATION_JSON)
public class AiEditResource {

    @Inject
    SourceLinks sourceLinks;

    @Inject
    AcceptAiEdit acceptAiEdit;
    @Inject
    SpecificationRepository versions;

    @POST
    @Path("/{proposalId}/accept")
    public Response accept(@PathParam("id") String id, @PathParam("proposalId") String proposalId) {
        SpecificationVersion version = acceptAiEdit.run(Ids.parse(id), Ids.parse(proposalId));
        return Response.status(Response.Status.CREATED).entity(sourceLinks.complete(SessionViews.specification(version,
                versions.discussionOf(version.id()).orElse(null)))).build();
    }
}
