package dev.specwriter.infrastructure.web;

import dev.specwriter.application.sources.ManageSourceRoots;
import dev.specwriter.domain.port.SourceTree;
import dev.specwriter.domain.sources.Module;
import dev.specwriter.domain.sources.SourceRoot;
import dev.specwriter.infrastructure.sources.LocalSourceTree;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Racines de sources d'un projet (013, contracts/api.md) : déclarées, modifiées, supprimées, et
 * aperçu des modules d'un dossier. Chemins et motifs ne sont jamais journalisés.
 */
@Path("/api/projects/{id}/source-roots")
@Produces(MediaType.APPLICATION_JSON)
public class SourceRootResource {

    /** Modules renvoyés au plus par l'aperçu ; {@code moduleCount} donne le total exact. */
    static final int PREVIEW_MODULES = 500;

    public record SourceRootInput(String location, List<String> exclusions) {
    }

    public record ExclusionsInput(List<String> exclusions) {
    }

    /** {@code state} : OK, MISSING ou UNREADABLE, évalué à la lecture. */
    public record SourceRootView(UUID id, String location, String label, List<String> exclusions, Instant createdAt,
            String state) {

        static SourceRootView of(SourceRoot r) {
            SourceTree.RootProblem problem = LocalSourceTree.problem(java.nio.file.Path.of(r.location()));
            String state = problem == null ? "OK" : problem == SourceTree.RootProblem.NOT_FOUND ? "MISSING" : "UNREADABLE";
            return new SourceRootView(r.id(), r.location(), r.label(), r.exclusions().patterns(), r.createdAt(), state);
        }
    }

    public record ModuleView(String name, String path, String kind) {
    }

    public record SourceRootPreview(List<ModuleView> modules, int moduleCount, int retainedFiles, int excludedFiles,
            int sensitiveFiles, int ignoredByGit, boolean limitReached) {

        static SourceRootPreview of(SourceTree.Preview p) {
            List<ModuleView> modules = p.modules().stream().limit(PREVIEW_MODULES)
                    .map((Module m) -> new ModuleView(m.name(), m.path(), m.kind().name())).toList();
            return new SourceRootPreview(modules, p.modules().size(), p.retainedFiles(), p.excludedFiles(),
                    p.sensitiveFiles(), p.ignoredByGit(), p.limitReached());
        }
    }

    @Inject
    ManageSourceRoots manage;

    @GET
    public List<SourceRootView> list(@PathParam("id") String id) {
        return manage.list(ProjectResource.projectId(id)).stream().map(SourceRootView::of).toList();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response add(@PathParam("id") String id, SourceRootInput input) {
        SourceRootInput in = input == null ? new SourceRootInput(null, null) : input;
        SourceRoot root = manage.add(ProjectResource.projectId(id), in.location(), lines(in.exclusions()));
        return Response.status(Response.Status.CREATED).entity(SourceRootView.of(root)).build();
    }

    @PUT
    @Path("/{rootId}/exclusions")
    @Consumes(MediaType.APPLICATION_JSON)
    public SourceRootView setExclusions(@PathParam("id") String id, @PathParam("rootId") String rootId,
            ExclusionsInput input) {
        return SourceRootView.of(manage.setExclusions(ProjectResource.projectId(id), Ids.parse(rootId),
                lines(input == null ? null : input.exclusions())));
    }

    @DELETE
    @Path("/{rootId}")
    public Response delete(@PathParam("id") String id, @PathParam("rootId") String rootId) {
        manage.delete(ProjectResource.projectId(id), Ids.parse(rootId));
        return Response.noContent().build();
    }

    @POST
    @Path("/preview")
    @Consumes(MediaType.APPLICATION_JSON)
    public SourceRootPreview preview(@PathParam("id") String id, SourceRootInput input) {
        SourceRootInput in = input == null ? new SourceRootInput(null, null) : input;
        return SourceRootPreview.of(manage.preview(ProjectResource.projectId(id), in.location(), lines(in.exclusions())));
    }

    private static List<String> lines(List<String> exclusions) {
        return exclusions == null ? List.of() : exclusions;
    }
}
