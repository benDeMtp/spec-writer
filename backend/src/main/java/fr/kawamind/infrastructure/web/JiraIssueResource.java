package fr.kawamind.infrastructure.web;

import fr.kawamind.application.jira.GetJiraIssue;
import fr.kawamind.application.jira.ImportJiraIssue;
import fr.kawamind.application.session.SessionSnapshot;
import fr.kawamind.domain.question.Questionnaire;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/** {@code /api/jira/issues/{key}} : détail d'un ticket et import en session liée (FR-043, FR-046). */
@Path("/api/jira/issues/{key}")
@Produces(MediaType.APPLICATION_JSON)
public class JiraIssueResource {

    static final String TRUNCATED = "La description du ticket dépassait 10 000 caractères : elle a été tronquée.";

    @Inject
    GetJiraIssue getJiraIssue;
    @Inject
    ImportJiraIssue importJiraIssue;

    @GET
    public GetJiraIssue.IssueView get(@PathParam("key") String key) {
        return getJiraIssue.run(key);
    }

    /** Crée la session liée ; Jira n'est pas modifié. */
    @POST
    @Path("/import")
    public Response importIssue(@PathParam("key") String key) {
        ImportJiraIssue.Imported imported = importJiraIssue.run(key);
        SessionSnapshot snapshot = new SessionSnapshot(imported.session(), Questionnaire.empty(),
                imported.truncated() ? List.of(TRUNCATED) : List.of());
        return Response.status(imported.existing() ? Response.Status.OK : Response.Status.CREATED)
                .entity(SessionViews.detail(snapshot, imported.version().number(), imported.ticket())).build();
    }
}
