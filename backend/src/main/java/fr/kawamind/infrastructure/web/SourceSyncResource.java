package fr.kawamind.infrastructure.web;

import fr.kawamind.application.knowledge.ManageSourceSync;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

/** {@code /api/projects/{projectId}/source-sync} : délais et bilans des sources (017, US2). */
@Path("/api/projects/{projectId}/source-sync")
@Produces(MediaType.APPLICATION_JSON)
public class SourceSyncResource {

    /** Délai en minutes ; {@code null} désactive. */
    public record IntervalInput(Integer intervalMinutes) {
    }

    @Inject
    ManageSourceSync manage;

    @GET
    public List<ManageSourceSync.State> list(@PathParam("projectId") UUID projectId) {
        return manage.list(projectId);
    }

    @PUT
    @Path("/{sourceKey}")
    @Consumes(MediaType.APPLICATION_JSON)
    public ManageSourceSync.State setInterval(@PathParam("projectId") UUID projectId,
            @PathParam("sourceKey") String sourceKey, IntervalInput input) {
        return manage.setInterval(projectId, sourceKey, input == null ? null : input.intervalMinutes());
    }
}
