package dev.specwriter.infrastructure.web;

import dev.specwriter.application.conversation.Converse;
import dev.specwriter.application.conversation.ManageConversations;
import dev.specwriter.domain.conversation.Conversation;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/** Conversations d'un projet (010, contracts/api.md) : liste et création. */
@Path("/api/projects/{id}/conversations")
@Produces(MediaType.APPLICATION_JSON)
public class ProjectConversationResource {

    @Inject
    Converse converse;

    @Inject
    ManageConversations manage;

    @GET
    public List<Conversation> list(@PathParam("id") String id) {
        return manage.list(ProjectResource.projectId(id));
    }

    @POST
    public Response create(@PathParam("id") String id) {
        return Response.status(Response.Status.CREATED).entity(converse.create(ProjectResource.projectId(id))).build();
    }
}
