package dev.specwriter.infrastructure.web;

import dev.specwriter.application.attachment.AttachmentDrafts;
import dev.specwriter.domain.attachment.Attachment;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * Documents joints (009, contracts/api.md) : le contenu brut du fichier est reçu, lu aussitôt, et
 * seul le texte extrait est gardé. Les réponses ne contiennent jamais ce texte.
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class AttachmentResource {

    @Inject
    AttachmentDrafts drafts;

    /** Document joint sur l'écran du besoin, avant le démarrage. */
    @POST
    @Path("/attachment-drafts")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response createDraft(@QueryParam("name") String name, byte[] content) {
        Attachment.View view = drafts.create(name, content == null ? new byte[0] : content).view();
        return Response.status(Response.Status.CREATED).entity(view).build();
    }

    @DELETE
    @Path("/attachment-drafts/{draftId}")
    public Response forgetDraft(@PathParam("draftId") String draftId) {
        drafts.forget(List.of(Ids.parse(draftId)));
        return Response.noContent().build();
    }
}
