package dev.specwriter.infrastructure.web;

import dev.specwriter.application.jira.ListProjectIssues;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/** {@code /api/jira/projects/{key}/issues} : tickets du projet Jira par pages de 25 (FR-020, FR-021 de 002). */
@Path("/api/jira/projects/{key}/issues")
@Produces(MediaType.APPLICATION_JSON)
public class JiraProjectIssuesResource {

    @Inject
    ListProjectIssues listProjectIssues;

    @GET
    public ListProjectIssues.IssuePage list(@PathParam("key") String key,
            @QueryParam("startAt") @DefaultValue("0") int startAt) {
        return listProjectIssues.run(key, startAt);
    }
}
