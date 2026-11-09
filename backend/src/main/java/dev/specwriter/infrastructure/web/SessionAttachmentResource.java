package dev.specwriter.infrastructure.web;

import dev.specwriter.application.attachment.ManageAttachments;
import dev.specwriter.domain.attachment.Attachment;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * Documents joints d'une session démarrée (009, US3, contracts/api.md) : liste sans texte, ajout par
 * contenu brut, retrait. Refusés sur une feature implémentée.
 */
@Path("/api/sessions/{id}/attachments")
@Produces(MediaType.APPLICATION_JSON)
public class SessionAttachmentResource {

    @Inject
    ManageAttachments manage;

    /** Documents de la session dans l'ordre d'ajout, sans leur texte. */
    @GET
    public List<Attachment.View> list(@PathParam("id") String id) {
        return manage.list(Ids.parse(id));
    }

    /** Document joint depuis le questionnaire ou la spécification ; refusé sur une feature implémentée. */
    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response add(@PathParam("id") String id, @QueryParam("name") String name, byte[] content) {
        Attachment.View view = manage.add(Ids.parse(id), name, content == null ? new byte[0] : content);
        return Response.status(Response.Status.CREATED).entity(view).build();
    }

    @DELETE
    @Path("/{attachmentId}")
    public Response remove(@PathParam("id") String id, @PathParam("attachmentId") String attachmentId) {
        manage.remove(Ids.parse(id), Ids.parse(attachmentId));
        return Response.noContent().build();
    }
}
