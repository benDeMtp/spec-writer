package fr.kawamind.infrastructure.web;

import fr.kawamind.application.jira.ListProjectIssues;
import fr.kawamind.domain.jira.IssueSort;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

/** {@code /api/projects/{projectId}/jira-issues} : tickets de l'index, triés et paginés (017, US1). */
@Path("/api/projects/{projectId}/jira-issues")
@Produces(MediaType.APPLICATION_JSON)
public class ProjectIssuesResource {

    public static final int DEFAULT_SIZE = 50;

    @Inject
    ListProjectIssues listProjectIssues;

    @GET
    public ListProjectIssues.IssuePage list(@PathParam("projectId") UUID projectId, @QueryParam("sort") String sort,
            @QueryParam("dir") String dir, @QueryParam("startAt") @DefaultValue("0") int startAt,
            @QueryParam("size") @DefaultValue("50") int size,
            @QueryParam("labelled") @DefaultValue("true") boolean labelled,
            @QueryParam("done") @DefaultValue("false") boolean done) {
        return listProjectIssues.run(projectId, IssueSort.parse(sort, dir), labelled, done, startAt, size);
    }
}
