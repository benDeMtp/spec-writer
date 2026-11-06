package dev.specwriter.infrastructure.web;

import dev.specwriter.application.knowledge.IndexationError;
import dev.specwriter.application.knowledge.Indexations;
import dev.specwriter.application.knowledge.AdrList;
import dev.specwriter.application.knowledge.Indexations.Snapshot;
import dev.specwriter.application.knowledge.ListAdrs;
import dev.specwriter.application.knowledge.TicketSummary;
import dev.specwriter.application.knowledge.KnowledgeQueries;
import dev.specwriter.application.knowledge.ManageArchitectureSource;
import dev.specwriter.application.knowledge.SynchronizeKnowledge.Mode;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.infrastructure.web.ProjectResource.ProjectView;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Connaissance d'un projet (contracts/openapi.yaml de 002) : workspace Structurizr, indexations,
 * état de l'index et recherche. Le contenu indexé n'est renvoyé qu'en extraits.
 */
@Path("/api/projects/{id}")
@Produces(MediaType.APPLICATION_JSON)
public class KnowledgeResource {

    public record ArchitectureSourceInput(String workspace, String ownership) {
    }

    public record IndexationInput(String mode) {
    }

    public record IndexationView(String status, String mode, String step, String current, int done, int total,
            Instant startedAt, Instant stepStartedAt, Instant finishedAt, List<IndexationError> errors,
            TicketSummary tickets) {

        static IndexationView of(Snapshot s) {
            return new IndexationView(s.status().name(), s.mode().name(), s.step() == null ? null : s.step().name(),
                    s.current(), s.done(), s.total(), s.startedAt(), s.stepStartedAt(), s.finishedAt(), s.errors(),
                    s.tickets());
        }
    }

    @Inject
    ProjectResource projectViews;
    @Inject
    ProjectRepository projects;
    @Inject
    ManageArchitectureSource architecture;
    @Inject
    Indexations indexations;
    @Inject
    KnowledgeQueries queries;
    @Inject
    ListAdrs listAdrs;

    @PUT
    @Path("/architecture-source")
    @Consumes(MediaType.APPLICATION_JSON)
    public ProjectView setArchitecture(@PathParam("id") String id, ArchitectureSourceInput input) {
        UUID projectId = ProjectResource.projectId(id);
        ArchitectureSourceInput in = input == null ? new ArchitectureSourceInput(null, null) : input;
        architecture.set(projectId, in.workspace(), in.ownership());
        return projectViews.view(projects.find(projectId).orElseThrow(() -> new DomainException(ErrorCode.PROJECT_NOT_FOUND)));
    }

    @DELETE
    @Path("/architecture-source")
    public void clearArchitecture(@PathParam("id") String id) {
        architecture.clear(ProjectResource.projectId(id));
    }

    @POST
    @Path("/indexations")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response startIndexation(@PathParam("id") String id, IndexationInput input) {
        Snapshot s = indexations.start(ProjectResource.projectId(id), mode(input == null ? null : input.mode()));
        return Response.status(Response.Status.ACCEPTED).entity(IndexationView.of(s)).build();
    }

    @GET
    @Path("/indexations/current")
    public IndexationView currentIndexation(@PathParam("id") String id) {
        return indexations.current(ProjectResource.projectId(id)).map(IndexationView::of)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Aucune indexation pour ce projet."));
    }

    @DELETE
    @Path("/indexations/current")
    public void cancelIndexation(@PathParam("id") String id) {
        indexations.cancel(ProjectResource.projectId(id));
    }

    @GET
    @Path("/knowledge")
    public KnowledgeQueries.Status knowledge(@PathParam("id") String id) {
        return queries.status(ProjectResource.projectId(id));
    }

    /** ADR du projet pour la page des ADR (feature 004, contracts/api.md). */
    @GET
    @Path("/knowledge/adrs")
    public AdrList adrs(@PathParam("id") String id) {
        return listAdrs.run(ProjectResource.projectId(id));
    }

    @GET
    @Path("/knowledge/search")
    public List<KnowledgeQueries.Hit> search(@PathParam("id") String id, @QueryParam("q") String q,
            @QueryParam("kinds") List<String> kinds) {
        return queries.search(ProjectResource.projectId(id), q, kinds(kinds));
    }

    private static Mode mode(String raw) {
        if (raw == null || raw.isBlank()) {
            return Mode.INCREMENTAL;
        }
        try {
            return Mode.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Mode inconnu : FULL ou INCREMENTAL.");
        }
    }

    private static Set<KnowledgeKind> kinds(List<String> raw) {
        Set<KnowledgeKind> kinds = EnumSet.noneOf(KnowledgeKind.class);
        for (String value : raw == null ? List.<String>of() : raw) {
            for (String part : value.split(",")) {
                if (part.isBlank()) {
                    continue;
                }
                try {
                    kinds.add(KnowledgeKind.valueOf(part.strip()));
                } catch (IllegalArgumentException e) {
                    throw new DomainException(ErrorCode.VALIDATION_FAILED,
                            "Type inconnu : TICKET, DOC_SECTION, ADR ou ARCH_ELEMENT.");
                }
            }
        }
        return kinds;
    }
}
