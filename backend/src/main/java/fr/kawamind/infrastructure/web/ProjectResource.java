package fr.kawamind.infrastructure.web;

import fr.kawamind.application.jira.JiraAccess;
import fr.kawamind.application.project.CheckJiraProjectKey;
import fr.kawamind.application.project.CreateProject;
import fr.kawamind.application.project.CurrentProject;
import fr.kawamind.application.project.DeleteProject;
import fr.kawamind.application.project.SelectCurrentProject;
import fr.kawamind.application.project.UpdateProject;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.DocSourceRepository;
import fr.kawamind.application.knowledge.KnowledgeQueries;
import fr.kawamind.domain.knowledge.KnowledgeSource;
import fr.kawamind.domain.port.KnowledgeSourceRepository;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.port.SourceRootRepository;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.project.DocSource;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.session.SessionListing;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** {@code /api/projects*} : projets et projet courant (contracts/openapi.yaml de 002, US1). */
@Path("/api/projects")
@Produces(MediaType.APPLICATION_JSON)
public class ProjectResource {

    public record ProjectInput(String name, String description, String jiraProjectKey) {
    }

    public record CurrentRequest(String projectId) {
    }

    public record ProjectView(UUID id, String name, String description, String jiraProjectKey,
            String effectiveJiraProjectKey, String jiraKeyWarning, int sessionCount, String context,
            Instant contextUpdatedAt, List<DocSourceView> docSources, String architectureWorkspace,
            String architectureOwnership, KnowledgeQueries.Status knowledge,
            List<SourceRootResource.SourceRootView> sourceRoots) {
    }

    public record DocSourceView(UUID id, String kind, String location, Instant lastReadAt, Integer pagesRead,
            Integer pagesInError, List<String> exclusions) {

        static DocSourceView of(DocSource s) {
            DocSource.ReadSummary r = s.lastResult();
            return new DocSourceView(s.id(), s.kind().name(), s.location(), s.lastReadAt(), r == null ? null : r.pagesRead(),
                    r == null ? null : r.pagesInError(), s.exclusions().patterns());
        }
    }

    @Inject
    ProjectRepository projects;
    @Inject
    KnowledgeSourceRepository knowledgeSources;
    @Inject
    SourceRootRepository sourceRoots;
    @Inject
    KnowledgeQueries knowledge;
    @Inject
    SessionRepository sessions;
    @Inject
    DocSourceRepository docSources;
    @Inject
    JiraAccess jira;
    @Inject
    CheckJiraProjectKey checkKey;
    @Inject
    CreateProject createProject;
    @Inject
    UpdateProject updateProject;
    @Inject
    DeleteProject deleteProject;
    @Inject
    SelectCurrentProject selectCurrentProject;

    @GET
    public List<ProjectView> list() {
        Map<UUID, Long> counts = sessionCounts();
        return projects.list().stream().map(p -> view(p, null, counts)).toList();
    }

    @GET
    @Path("/current")
    public ProjectView current() {
        return view(CurrentProject.resolve(projects), null, sessionCounts());
    }

    @PUT
    @Path("/current")
    @Consumes(MediaType.APPLICATION_JSON)
    public void select(CurrentRequest request) {
        selectCurrentProject.run(Ids.parse(request == null ? null : request.projectId()));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(ProjectInput input) {
        ProjectInput in = input == null ? new ProjectInput(null, null, null) : input;
        Project created = createProject.run(in.name(), in.description(), in.jiraProjectKey());
        return Response.status(Response.Status.CREATED).entity(withWarning(created)).build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public ProjectView update(@PathParam("id") String id, ProjectInput input) {
        ProjectInput in = input == null ? new ProjectInput(null, null, null) : input;
        return withWarning(updateProject.run(projectId(id), in.name(), in.description(), in.jiraProjectKey()));
    }

    @DELETE
    @Path("/{id}")
    public void delete(@PathParam("id") String id, @QueryParam("sessions") String mode,
            @QueryParam("targetProjectId") String targetProjectId) {
        DeleteProject.Mode chosen;
        try {
            chosen = DeleteProject.Mode.valueOf(mode == null ? "" : mode);
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Choisissez de déplacer ou de supprimer les spécifications.");
        }
        UUID target = targetProjectId == null || targetProjectId.isBlank() ? null : projectId(targetProjectId);
        deleteProject.run(projectId(id), chosen, target);
    }

    /** Vue d'un projet, sans vérifier sa clé Jira. */
    ProjectView view(Project p) {
        return view(p, null, sessionCounts());
    }

    private ProjectView withWarning(Project p) {
        return view(p, checkKey.warning(p).orElse(null), sessionCounts());
    }

    private ProjectView view(Project p, String warning, Map<UUID, Long> counts) {
        KnowledgeSource source = knowledgeSources.find(p.id());
        return new ProjectView(p.id(), p.name().value(), p.description(),
                p.jiraProjectKey() == null ? null : p.jiraProjectKey().value(), jira.effectiveProject(p), warning,
                counts.getOrDefault(p.id(), 0L).intValue(), p.context().markdown(), p.context().updatedAt(),
                docSources.list(p.id()).stream().map(DocSourceView::of).toList(),
                source.workspace(), source.ownership(), knowledge.status(p.id()),
                sourceRoots.list(p.id()).stream().map(SourceRootResource.SourceRootView::of).toList());
    }

    private Map<UUID, Long> sessionCounts() {
        return sessions.list().stream().collect(Collectors.groupingBy(SessionListing::projectId, Collectors.counting()));
    }

    /** Un identifiant de projet mal formé désigne un projet introuvable. */
    static UUID projectId(String raw) {
        try {
            return Ids.parse(raw);
        } catch (DomainException e) {
            throw new DomainException(ErrorCode.PROJECT_NOT_FOUND);
        }
    }
}
