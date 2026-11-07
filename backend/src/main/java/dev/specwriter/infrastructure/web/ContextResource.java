package dev.specwriter.infrastructure.web;

import dev.specwriter.application.context.EditContext;
import dev.specwriter.application.context.ManageDocSources;
import dev.specwriter.application.knowledge.Indexations;
import dev.specwriter.application.knowledge.SynchronizeKnowledge;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.project.DocSource;
import dev.specwriter.infrastructure.web.ProjectResource.DocSourceView;
import dev.specwriter.infrastructure.web.ProjectResource.ProjectView;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

/**
 * Fiche générale d'un projet, rédigée à la main, et sources de documentation (FR-008, FR-013,
 * research.md R25). Les pages lues ne sont jamais renvoyées ni journalisées.
 */
@Path("/api/projects/{id}")
@Produces(MediaType.APPLICATION_JSON)
public class ContextResource {

    public record ContextInput(String markdown) {
    }

    /** {@code exclusions} : motifs d'un dossier de documents (006), un par élément. */
    public record DocSourceInput(String kind, String location, List<String> exclusions) {
    }

    public record ExclusionsInput(List<String> exclusions) {
    }

    public record PreviewInput(String location, List<String> exclusions) {
    }

    @Inject
    ProjectResource projectViews;
    @Inject
    EditContext editContext;
    @Inject
    ManageDocSources manageDocSources;
    @Inject
    Indexations indexations;

    @PUT
    @Path("/context")
    @Consumes(MediaType.APPLICATION_JSON)
    public ProjectView editContext(@PathParam("id") String id, ContextInput input) {
        return projectViews.view(editContext.run(ProjectResource.projectId(id), input == null ? null : input.markdown()));
    }

    @POST
    @Path("/doc-sources")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addSource(@PathParam("id") String id, DocSourceInput input) {
        DocSourceInput in = input == null ? new DocSourceInput(null, null, null) : input;
        UUID projectId = ProjectResource.projectId(id);
        DocSource source = manageDocSources.add(projectId, kind(in.kind()), in.location(),
                in.exclusions() == null ? List.of() : in.exclusions());
        // une nouvelle source se lit en entier, sans attendre (research.md R20)
        indexations.trigger(projectId, SynchronizeKnowledge.Mode.FULL);
        return Response.status(Response.Status.CREATED).entity(DocSourceView.of(source)).build();
    }

    /** Motifs d'un dossier de documents, pris en compte à sa prochaine indexation (006). */
    @PUT
    @Path("/doc-sources/{sourceId}/exclusions")
    @Consumes(MediaType.APPLICATION_JSON)
    public DocSourceView setExclusions(@PathParam("id") String id, @PathParam("sourceId") String sourceId,
            ExclusionsInput input) {
        return DocSourceView.of(manageDocSources.setExclusions(ProjectResource.projectId(id), Ids.parse(sourceId),
                input == null || input.exclusions() == null ? List.of() : input.exclusions()));
    }

    /** Effet des motifs sur un dossier, sans rien lire ni indexer (006, FR-010). */
    @POST
    @Path("/doc-sources/preview")
    @Consumes(MediaType.APPLICATION_JSON)
    public ManageDocSources.Preview preview(@PathParam("id") String id, PreviewInput input) {
        PreviewInput in = input == null ? new PreviewInput(null, null) : input;
        return manageDocSources.preview(ProjectResource.projectId(id), in.location(),
                in.exclusions() == null ? List.of() : in.exclusions());
    }

    @DELETE
    @Path("/doc-sources/{sourceId}")
    public void deleteSource(@PathParam("id") String id, @PathParam("sourceId") String sourceId) {
        manageDocSources.delete(ProjectResource.projectId(id), Ids.parse(sourceId));
    }

    private static DocSource.Kind kind(String raw) {
        try {
            return DocSource.Kind.valueOf(raw == null ? "" : raw);
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Choisissez une adresse (URL), un dossier Antora (FOLDER) ou un dossier de documents (DOCUMENTS).");
        }
    }
}
