package dev.specwriter.infrastructure.web;

import dev.specwriter.application.conversation.ManageConversations;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

/** Conversation dont vient une spécification (010, US2) : 204 si elle n'en vient pas ou a été supprimée. */
@Path("/api/sessions/{id}/conversation")
@Produces(MediaType.APPLICATION_JSON)
public class SessionConversationResource {

    public record Origin(UUID id, String name) {
    }

    @Inject
    ManageConversations manage;

    @GET
    public Response origin(@PathParam("id") String id) {
        return manage.origin(Ids.parse(id)).map(c -> Response.ok(new Origin(c.id(), c.name())).build())
                .orElseGet(() -> Response.noContent().build());
    }
}
