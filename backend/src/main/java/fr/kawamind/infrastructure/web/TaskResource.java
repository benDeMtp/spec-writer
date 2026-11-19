package fr.kawamind.infrastructure.web;

import fr.kawamind.application.task.EditProposal;
import fr.kawamind.application.task.EditTasks;
import fr.kawamind.application.task.ProposeTasks;
import fr.kawamind.application.task.ReadTasks;
import fr.kawamind.application.task.TasksView;
import fr.kawamind.application.task.TrackingActions;
import fr.kawamind.application.task.ValidateTasks;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.task.Basis;
import fr.kawamind.domain.task.BasisKind;
import fr.kawamind.domain.task.ProposedTask;
import fr.kawamind.domain.task.SpecTask;
import fr.kawamind.domain.task.TaskProposal;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
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
 * {@code /api/sessions/{id}/tasks} : tâches d'une spécification (015, contracts/rest-api.md). Même
 * protection que les autres ressources de session.
 */
@Path("/api/sessions/{id}/tasks")
@Produces(MediaType.APPLICATION_JSON)
public class TaskResource {

    public record BasisView(String kind, String ref) {

        static BasisView of(Basis b) {
            return new BasisView(b.kind().name(), b.ref());
        }

        Basis toBasis() {
            return kind == null ? Basis.NONE : new Basis(BasisKind.valueOf(kind), ref);
        }
    }

    public record TaskView(String id, int position, String title, String outcome, BasisView basis, String origin,
            String creation, String key, String url, String status, String jiraStatusName, String heldBy,
            Instant heldSince, boolean holderClosed, String pending, String report, List<String> dependsOn) {
    }

    public record Progress(int done, int total) {
    }

    public record TasksJson(String linkedIssueKey, boolean canPropose, String proposeBlocked, String tracking,
            Progress progress, Instant lastReadAt, String readProblem, int pendingSync, String creationProblem,
            boolean hasProposal, List<TaskView> tasks) {
    }

    public record ProposedView(String title, String outcome, BasisView basis, List<Integer> dependsOn, boolean manual) {
    }

    public record ExistingView(String key, String title) {
    }

    public record ProposalJson(String basis, List<ExistingView> existing, List<ProposedView> tasks) {
    }

    public record AddRequest(String title, String outcome, List<String> dependsOn) {
    }

    public record EditRequest(String title, Integer position, List<String> dependsOn) {
    }

    @Inject
    ReadTasks readTasks;
    @Inject
    ProposeTasks proposeTasks;
    @Inject
    EditProposal editProposal;
    @Inject
    ValidateTasks validateTasks;
    @Inject
    EditTasks editTasks;
    @Inject
    TrackingActions trackingActions;

    @GET
    public TasksJson list(@PathParam("id") String id) {
        return json(readTasks.run(Ids.parse(id)));
    }

    @GET
    @Path("/proposal")
    public Response proposal(@PathParam("id") String id) {
        UUID session = Ids.parse(id);
        return editProposal.current(session).map(p -> Response.ok(proposal(p, readTasks.current(session))).build())
                .orElseGet(() -> Response.status(Response.Status.NO_CONTENT).build());
    }

    @POST
    @Path("/proposal")
    public ProposalJson propose(@PathParam("id") String id) {
        UUID session = Ids.parse(id);
        TaskProposal p = proposeTasks.run(session);
        return proposal(p, readTasks.current(session));
    }

    @PUT
    @Path("/proposal")
    @Consumes(MediaType.APPLICATION_JSON)
    public ProposalJson saveProposal(@PathParam("id") String id, ProposalJson body) {
        UUID session = Ids.parse(id);
        if (body == null || body.tasks() == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "La proposition est vide.");
        }
        List<ProposedTask> tasks = body.tasks().stream().map(t -> new ProposedTask(t.title(), t.outcome(),
                t.basis() == null ? Basis.NONE : basis(t.basis()), t.dependsOn() == null ? List.of() : t.dependsOn(),
                t.manual())).toList();
        TaskProposal saved = editProposal.save(session, new TaskProposal(TaskProposal.Basing.USE_CASES, tasks));
        return proposal(saved, readTasks.current(session));
    }

    @DELETE
    @Path("/proposal")
    public Response discard(@PathParam("id") String id) {
        editProposal.discard(Ids.parse(id));
        return Response.noContent().build();
    }

    @POST
    @Path("/proposal/validate")
    public TasksJson validate(@PathParam("id") String id) {
        return json(validateTasks.run(Ids.parse(id)));
    }

    @POST
    @Path("/create-remaining")
    public TasksJson createRemaining(@PathParam("id") String id) {
        return json(validateTasks.createRemaining(Ids.parse(id)));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public TasksJson add(@PathParam("id") String id, AddRequest body) {
        if (body == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Indiquez le titre et le résultat de la tâche.");
        }
        return json(editTasks.add(Ids.parse(id), body.title(), body.outcome(), ids(body.dependsOn())));
    }

    @PATCH
    @Path("/{taskId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public TasksJson edit(@PathParam("id") String id, @PathParam("taskId") String taskId, EditRequest body) {
        if (body == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Aucune modification.");
        }
        return json(editTasks.edit(Ids.parse(id), Ids.parse(taskId), body.title(), body.position(),
                body.dependsOn() == null ? null : ids(body.dependsOn())));
    }

    @DELETE
    @Path("/{taskId}")
    public TasksJson delete(@PathParam("id") String id, @PathParam("taskId") String taskId) {
        return json(editTasks.delete(Ids.parse(id), Ids.parse(taskId)));
    }

    @POST
    @Path("/{taskId}/release")
    public TasksJson release(@PathParam("id") String id, @PathParam("taskId") String taskId) {
        return json(trackingActions.release(Ids.parse(id), Ids.parse(taskId)));
    }

    @POST
    @Path("/sync")
    public TasksJson sync(@PathParam("id") String id) {
        return json(trackingActions.sync(Ids.parse(id)));
    }

    @POST
    @Path("/resume")
    public TasksJson resume(@PathParam("id") String id) {
        return json(trackingActions.resume(Ids.parse(id)));
    }

    private static Basis basis(BasisView view) {
        try {
            return view.toBasis();
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Base de tâche inconnue.");
        }
    }

    private static List<UUID> ids(List<String> raw) {
        return raw == null ? List.of() : raw.stream().map(Ids::parse).toList();
    }

    static TasksJson json(TasksView v) {
        List<TaskView> tasks = v.tasks().stream().map(t -> task(t, v)).toList();
        return new TasksJson(v.linkedIssueKey(), v.proposeBlocked() == null,
                v.proposeBlocked() == null ? null : v.proposeBlocked().name(), v.tracking().name(),
                new Progress(v.done(), v.total()), v.lastReadAt(), v.readProblem() ? "JIRA_UNREACHABLE" : null,
                v.pendingSync(), v.creationProblem(), v.hasProposal(), tasks);
    }

    private static TaskView task(SpecTask t, TasksView v) {
        String url = t.jiraKey() == null || v.issueUrlPrefix() == null ? null : v.issueUrlPrefix() + t.jiraKey();
        return new TaskView(t.id().toString(), t.position(), t.title(), t.outcome(), BasisView.of(t.basis()),
                t.origin().name(), t.creation().name(), t.jiraKey(), url, t.status().name(), t.jiraStatusName(),
                t.holder() == null ? null : t.holder().agentName(), t.holder() == null ? null : t.holder().since(),
                v.closedHolders().contains(t.id()), t.pendingTarget() == null ? null : t.pendingTarget().name(),
                t.report(), t.dependsOn().stream().map(UUID::toString).toList());
    }

    private static ProposalJson proposal(TaskProposal p, TasksView v) {
        return new ProposalJson(p.basing().name(),
                v.tasks().stream().filter(SpecTask::isCreated).map(t -> new ExistingView(t.jiraKey(), t.title())).toList(),
                p.tasks().stream().map(t -> new ProposedView(t.title(), t.outcome(), BasisView.of(t.basis()),
                        t.dependsOn(), t.manual())).toList());
    }
}
