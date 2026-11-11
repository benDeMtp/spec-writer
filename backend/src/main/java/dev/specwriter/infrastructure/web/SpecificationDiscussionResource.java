package dev.specwriter.infrastructure.web;

import dev.specwriter.application.conversation.DiscussSpecification;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Discussion d'une spécification (011, contracts/api.md) : la discussion suivie (204 s'il n'y en a pas
 * encore), son ouverture, ou une nouvelle discussion ({@code fresh}). Les messages passent par les
 * routes des conversations.
 */
@Path("/api/sessions/{id}/discussion")
@Produces(MediaType.APPLICATION_JSON)
public class SpecificationDiscussionResource {

    public record OpenRequest(Boolean fresh) {
    }

    @Inject
    DiscussSpecification discuss;

    @GET
    public Response current(@PathParam("id") String id) {
        return discuss.current(Ids.parse(id)).map(v -> Response.ok(v).build())
                .orElseGet(() -> Response.noContent().build());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response open(@PathParam("id") String id, OpenRequest request) {
        boolean fresh = request != null && Boolean.TRUE.equals(request.fresh());
        return Response.status(Response.Status.CREATED)
                .entity(fresh ? discuss.fresh(Ids.parse(id)) : discuss.open(Ids.parse(id))).build();
    }
}
