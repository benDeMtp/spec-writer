package dev.specwriter.infrastructure.web;

import dev.specwriter.application.session.ManageSessions;
import dev.specwriter.application.specification.GenerateSpecification;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import jakarta.inject.Inject;
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
    SessionRepository sessions;
    @Inject
    SpecificationRepository versions;
    @Inject
    GenerateSpecification generateSpecification;

    @Inject
    ManageSessions manageSessions;

    @GET
    public List<SessionViews.VersionSummary> list(@PathParam("id") String id) {
        return manageSessions.versions(Ids.parse(id)).stream().map(SessionViews::versionSummary).toList();
    }

    /** 201 pour une nouvelle version, 200 quand la version existante est réutilisée. */
    @POST
    public Response generate(@PathParam("id") String id) {
        GenerateSpecification.Generated generated = generateSpecification.run(Ids.parse(id));
        return Response.status(generated.reused() ? Response.Status.OK : Response.Status.CREATED)
                .entity(SessionViews.specification(generated.version())).build();
    }

    @GET
    @Path("/{n}")
    public SessionViews.SpecificationView get(@PathParam("id") String id, @PathParam("n") int number) {
        UUID sessionId = existing(id);
        return versions.find(sessionId, number).map(SessionViews::specification)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    private UUID existing(String id) {
        UUID sessionId = Ids.parse(id);
        if (sessions.find(sessionId).isEmpty()) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
        return sessionId;
    }
}
