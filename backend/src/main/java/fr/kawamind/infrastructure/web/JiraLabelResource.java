package fr.kawamind.infrastructure.web;

import fr.kawamind.application.project.ManageJiraLabel;
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

/** {@code /api/projects/{id}/jira-label} : label de reconnaissance du projet (024, contracts/rest-api.md). */
@Path("/api/projects/{id}/jira-label")
@Produces(MediaType.APPLICATION_JSON)
public class JiraLabelResource {

    public record Input(String label) {
    }

    @Inject
    ManageJiraLabel labels;

    @GET
    public ManageJiraLabel.View get(@PathParam("id") String id) {
        return labels.view(ProjectResource.projectId(id));
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public ManageJiraLabel.View set(@PathParam("id") String id, Input input) {
        if (input == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        return labels.save(ProjectResource.projectId(id), input.label());
    }

    @DELETE
    public void remove(@PathParam("id") String id) {
        labels.remove(ProjectResource.projectId(id));
    }
}
