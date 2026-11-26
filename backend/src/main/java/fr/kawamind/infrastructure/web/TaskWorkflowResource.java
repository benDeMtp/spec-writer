package fr.kawamind.infrastructure.web;

import fr.kawamind.application.task.ManageTaskWorkflow;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/** {@code /api/projects/{id}/task-workflow} : suivi des tâches du projet dans Jira (023, contracts/rest-api.md). */
@Path("/api/projects/{id}/task-workflow")
@Produces(MediaType.APPLICATION_JSON)
public class TaskWorkflowResource {

    public record Input(String arrivalStatus, List<String> finishedStatuses) {
    }

    @Inject
    ManageTaskWorkflow workflows;

    @GET
    public ManageTaskWorkflow.View get(@PathParam("id") String id) {
        return workflows.view(ProjectResource.projectId(id));
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public ManageTaskWorkflow.View set(@PathParam("id") String id, Input input) {
        if (input == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        return workflows.save(ProjectResource.projectId(id), input.arrivalStatus(),
                input.finishedStatuses() == null ? List.of() : input.finishedStatuses());
    }

    @DELETE
    public void remove(@PathParam("id") String id) {
        workflows.remove(ProjectResource.projectId(id));
    }
}
