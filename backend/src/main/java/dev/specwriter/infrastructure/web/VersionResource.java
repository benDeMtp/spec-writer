package dev.specwriter.infrastructure.web;

import dev.specwriter.application.knowledge.ReportSubtasks;
import dev.specwriter.application.session.ManageSessions;
import dev.specwriter.application.specification.DeleteVersion;
import dev.specwriter.application.specification.GenerateSpecification;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.port.LinkedTicketRepository;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.specification.SpecificationVersion;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

/** {@code /api/sessions/{id}/versions} : génération et lecture des versions (FR-014, FR-018). */
@Path("/api/sessions/{id}/versions")
@Produces(MediaType.APPLICATION_JSON)
public class VersionResource {

    @Inject
    SourceLinks sourceLinks;

    @Inject
    SessionRepository sessions;
    @Inject
    SpecificationRepository versions;
    @Inject
    GenerateSpecification generateSpecification;
    @Inject
    LinkedTicketRepository tickets;

    @Inject
    ManageSessions manageSessions;
    @Inject
    DeleteVersion deleteVersion;
    @Inject
    ReportSubtasks reportSubtasks;

    @GET
    public List<SessionViews.VersionSummary> list(@PathParam("id") String id) {
        UUID sessionId = Ids.parse(id);
        LinkedJiraTicket ticket = tickets.findBySession(sessionId).orElse(null);
        return manageSessions.versions(sessionId).stream().map(v -> SessionViews.versionSummary(v, ticket)).toList();
    }

    /** 201 pour une nouvelle version, 200 quand la version existante est réutilisée. */
    @POST
    public Response generate(@PathParam("id") String id) {
        GenerateSpecification.Generated generated = generateSpecification.run(Ids.parse(id));
        return Response.status(generated.reused() ? Response.Status.OK : Response.Status.CREATED)
                .entity(sourceLinks.complete(SessionViews.specification(generated.version()))).build();
    }

    @GET
    @Path("/{n}")
    public SessionViews.SpecificationView get(@PathParam("id") String id, @PathParam("n") int number) {
        UUID sessionId = existing(id);
        return versions.find(sessionId, number).map(SessionViews::specification).map(sourceLinks::complete)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    /**
     * Rubrique « Sous-tâches de <clé> » (FR-046, FR-047, research.md R29), calculée à la demande ;
     * 204 sans ticket lié ni sous-tâche ni parent. N'écrit jamais dans Jira.
     */
    @GET
    @Path("/{n}/subtasks")
    public Response subtasks(@PathParam("id") String id, @PathParam("n") int number) {
        return reportSubtasks.run(Ids.parse(id), number).map(r -> Response.ok(r).build())
                .orElseGet(() -> Response.noContent().build());
    }

    /** Markdown de la version, en pièce jointe (FR-019). La confirmation d'écrasement revient au navigateur. */
    @GET
    @Path("/{n}/export")
    @Produces("text/markdown; charset=UTF-8")
    public Response export(@PathParam("id") String id, @PathParam("n") int number) {
        UUID sessionId = Ids.parse(id);
        Session session = sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        SpecificationVersion version = versions.find(sessionId, number)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        return Response.ok(version.markdown(), "text/markdown; charset=UTF-8")
                .header("Content-Disposition", ExportFileName.contentDisposition(ExportFileName.of(session.name(), number)))
                .build();
    }

    /**
     * Supprime une version locale (FR-014 de 002) ; 409 pendant une opération de l'IA (FR-019).
     * Jira n'est pas modifié.
     */
    @DELETE
    @Path("/{n}")
    public void delete(@PathParam("id") String id, @PathParam("n") int number) {
        deleteVersion.run(Ids.parse(id), number);
    }

    private UUID existing(String id) {
        UUID sessionId = Ids.parse(id);
        if (sessions.find(sessionId).isEmpty()) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
        return sessionId;
    }
}
