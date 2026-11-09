package dev.specwriter.infrastructure.web;

import dev.specwriter.application.conversation.ConversationAttachments;
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

/** Documents joints d'une conversation (010, US3) : comme ceux d'une session (009). */
@Path("/api/conversations/{cid}/attachments")
@Produces(MediaType.APPLICATION_JSON)
public class ConversationAttachmentResource {

    @Inject
    ConversationAttachments attachments;

    @GET
    public List<Attachment.View> list(@PathParam("cid") String cid) {
        return attachments.list(Ids.parse(cid));
    }

    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response add(@PathParam("cid") String cid, @QueryParam("name") String name, byte[] content) {
        Attachment.View view = attachments.add(Ids.parse(cid), name, content == null ? new byte[0] : content);
        return Response.status(Response.Status.CREATED).entity(view).build();
    }

    @DELETE
    @Path("/{attachmentId}")
    public Response remove(@PathParam("cid") String cid, @PathParam("attachmentId") String attachmentId) {
        attachments.remove(Ids.parse(cid), Ids.parse(attachmentId));
        return Response.noContent().build();
    }
}
