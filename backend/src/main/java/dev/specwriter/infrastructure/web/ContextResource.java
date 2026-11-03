package dev.specwriter.infrastructure.web;

import dev.specwriter.application.context.AcceptContextProposal;
import dev.specwriter.application.context.ContextProposalStore.ContextProposal;
import dev.specwriter.application.context.EditContext;
import dev.specwriter.application.context.ManageDocSources;
import dev.specwriter.application.context.PreviewContextSources;
import dev.specwriter.application.context.ProposeProjectContext;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.DocumentationReader.Unread;
import dev.specwriter.domain.project.DocSource;
import dev.specwriter.infrastructure.web.ProjectResource.DocSourceView;
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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

/**
 * Contexte d'un projet : fiche modifiée à la main, sources de documentation et propositions de
 * l'IA (FR-013 à FR-013e). Les pages et les tickets lus ne sont jamais renvoyés ni journalisés.
 */
@Path("/api/projects/{id}")
@Produces(MediaType.APPLICATION_JSON)
public class ContextResource {

    public record ContextInput(String markdown) {
    }

    public record DocSourceInput(String kind, String location) {
    }

    public record PreviewView(int pages, int tickets, List<Unread> unreadable) {
    }

    public record ContextProposalView(UUID proposalId, String markdown, String unifiedDiff, List<String> sources,
            List<Unread> unread) {

        static ContextProposalView of(ContextProposal p) {
            return new ContextProposalView(p.id(), p.markdown(), p.unifiedDiff(), p.sources(), p.unread());
        }
    }

    @Inject
    ProjectResource projectViews;
    @Inject
    EditContext editContext;
    @Inject
    ManageDocSources manageDocSources;
    @Inject
    PreviewContextSources previewSources;
    @Inject
    ProposeProjectContext proposeContext;
    @Inject
    AcceptContextProposal acceptProposal;

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
        DocSourceInput in = input == null ? new DocSourceInput(null, null) : input;
        DocSource source = manageDocSources.add(ProjectResource.projectId(id), kind(in.kind()), in.location());
        return Response.status(Response.Status.CREATED).entity(DocSourceView.of(source)).build();
    }

    @DELETE
    @Path("/doc-sources/{sourceId}")
    public void deleteSource(@PathParam("id") String id, @PathParam("sourceId") String sourceId) {
        manageDocSources.delete(ProjectResource.projectId(id), Ids.parse(sourceId));
    }

    @GET
    @Path("/context-proposals/preview")
    public PreviewView preview(@PathParam("id") String id) {
        PreviewContextSources.Preview p = previewSources.run(ProjectResource.projectId(id));
        return new PreviewView(p.pages(), p.tickets(), p.unreadable());
    }

    @POST
    @Path("/context-proposals")
    public Response propose(@PathParam("id") String id) {
        ContextProposal p = proposeContext.run(ProjectResource.projectId(id));
        return Response.status(Response.Status.CREATED).entity(ContextProposalView.of(p)).build();
    }

    @POST
    @Path("/context-proposals/{proposalId}/accept")
    public ProjectView accept(@PathParam("id") String id, @PathParam("proposalId") String proposalId) {
        return projectViews.view(acceptProposal.run(ProjectResource.projectId(id), Ids.parse(proposalId)));
    }

    private static DocSource.Kind kind(String raw) {
        try {
            return DocSource.Kind.valueOf(raw == null ? "" : raw);
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Choisissez une adresse (URL) ou un dossier (FOLDER).");
        }
    }
}
