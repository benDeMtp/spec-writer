package fr.kawamind.infrastructure.web;

import fr.kawamind.application.interview.SourceUpdates;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * « À reporter » d'une spécification (021, US6, contracts/rest-api.md). Aucune route n'écrit dans
 * Confluence, OneDrive, SharePoint ou Jira (FR-030) : la mise à jour de la source reste manuelle.
 */
@Path("/api/sessions/{sid}/source-updates")
@Produces(MediaType.APPLICATION_JSON)
public class SourceUpdateResource {

    public record SourceView(String kind, String ref, String title, String link) {
    }

    /** {@code state} : {@code OPEN}, ou {@code SOURCE_CHANGED} quand la source a changé depuis la décision. */
    public record SourceUpdateView(UUID id, SourceView source, String text, String state, UUID interviewId,
            Instant decidedAt) {
    }

    @Inject
    SourceUpdates updates;

    @Inject
    SourceLinks links;

    @GET
    public List<SourceUpdateView> list(@PathParam("sid") String sid) {
        return updates.open(Ids.parse(sid)).stream().map(l -> {
            var u = l.update();
            return new SourceUpdateView(u.id(), new SourceView(u.kind().name(), u.ref(), u.title(),
                    links.link(u.kind(), u.ref())), u.text(), l.sourceChanged() ? "SOURCE_CHANGED" : "OPEN",
                    u.interviewId(), u.createdAt());
        }).toList();
    }

    @POST
    @Path("/{uid}/close")
    public void close(@PathParam("sid") String sid, @PathParam("uid") String uid) {
        updates.close(Ids.parse(sid), Ids.parse(uid));
    }
}
