package fr.kawamind.infrastructure.web;

import fr.kawamind.application.context.EditContext;
import fr.kawamind.application.context.ManageDocSources;
import fr.kawamind.application.knowledge.Indexations;
import fr.kawamind.application.knowledge.SynchronizeKnowledge;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.project.DocSource;
import fr.kawamind.infrastructure.web.ProjectResource.DocSourceView;
import fr.kawamind.infrastructure.web.ProjectResource.ProjectView;
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

    /**
     * {@code exclusions} : motifs d'un dossier de documents (006), un par élément ;
     * {@code reliability} : niveau propre d'une racine Confluence (016), facultatif.
     */
    public record DocSourceInput(String kind, String location, List<String> exclusions, String reliability) {
    }

    public record ReliabilityInput(String reliability) {
    }

    public record ExclusionsInput(List<String> exclusions) {
    }

    /** {@code kind} : {@code CONFLUENCE} pour l'aperçu d'une racine (016), dossier de documents sinon. */
    public record PreviewInput(String kind, String location, List<String> exclusions) {
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
        DocSourceInput in = input == null ? new DocSourceInput(null, null, null, null) : input;
        UUID projectId = ProjectResource.projectId(id);
        DocSource.Kind kind = kind(in.kind());
        DocSource source = kind == DocSource.Kind.CONFLUENCE
                ? manageDocSources.addConfluence(projectId, in.location(), ReliabilityResource.level(in.reliability()))
                : manageDocSources.add(projectId, kind, in.location(),
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

    /**
     * Effet des motifs sur un dossier, sans rien lire ni indexer (006, FR-010) ; aperçu d'une racine
     * Confluence : titre et nombre de pages, ou le motif du refus (016, FR-004).
     */
    @POST
    @Path("/doc-sources/preview")
    @Consumes(MediaType.APPLICATION_JSON)
    public Object preview(@PathParam("id") String id, PreviewInput input) {
        PreviewInput in = input == null ? new PreviewInput(null, null, null) : input;
        if ("CONFLUENCE".equals(in.kind())) {
            return manageDocSources.previewConfluence(ProjectResource.projectId(id), in.location());
        }
        return manageDocSources.preview(ProjectResource.projectId(id), in.location(),
                in.exclusions() == null ? List.of() : in.exclusions());
    }

    /** Niveau propre d'une racine Confluence (016, FR-016a) ; {@code null} : celui de Confluence. */
    @PUT
    @Path("/doc-sources/{sourceId}/reliability")
    @Consumes(MediaType.APPLICATION_JSON)
    public DocSourceView setReliability(@PathParam("id") String id, @PathParam("sourceId") String sourceId,
            ReliabilityInput input) {
        return DocSourceView.of(manageDocSources.setReliability(ProjectResource.projectId(id), Ids.parse(sourceId),
                ReliabilityResource.level(input == null ? null : input.reliability())));
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
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Choisissez une adresse (URL), un dossier Antora (FOLDER), un dossier de documents (DOCUMENTS) ou une page Confluence (CONFLUENCE).");
        }
    }
}
