package dev.specwriter.infrastructure.web;

import dev.specwriter.application.jira.PushToJira;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorAction;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.ConflictResolution;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.Arrays;

/** {@code /api/sessions/{id}/jira} : envoi de la description vers le ticket lié (FR-047, FR-048). */
@Path("/api/sessions/{id}/jira")
@Produces(MediaType.APPLICATION_JSON)
public class JiraLinkResource {

    public record PushRequest(Boolean confirm, Integer versionNumber, String conflictResolution) {
    }

    /** 409 : erreur au format Problem, avec les deux versions à départager. */
    public record ConflictProblem(String type, String title, int status, String code, String userMessage,
            ErrorAction action, String jiraDescriptionMarkdown, String localMarkdown, Instant jiraUpdated) {

        static ConflictProblem of(PushToJira.Conflict c) {
            Problem p = Problem.of(ErrorCode.JIRA_CONFLICT);
            return new ConflictProblem(p.type(), p.title(), p.status(), p.code(), p.userMessage(), p.action(),
                    c.jiraDescriptionMarkdown(), c.localMarkdown(), c.jiraUpdated());
        }
    }

    @Inject
    PushToJira pushToJira;

    /**
     * 200 et le ticket lié après un envoi ou une reprise de la version Jira ; 202 et la proposition
     * de fusion pour {@code AI_MERGE} ; 409 en cas de conflit.
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response push(@PathParam("id") String id, PushRequest request) {
        if (request == null || request.versionNumber() == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Indiquez la version à envoyer.");
        }
        PushToJira.Outcome outcome = pushToJira.run(Ids.parse(id), request.versionNumber(),
                Boolean.TRUE.equals(request.confirm()), resolution(request.conflictResolution()));
        return switch (outcome) {
            case PushToJira.Pushed p -> Response.ok(SessionViews.ticket(p.ticket())).build();
            case PushToJira.TakenFromJira t -> Response.ok(SessionViews.ticket(t.ticket())).build();
            case PushToJira.MergeProposed m -> Response.accepted(SessionViews.proposal(m.proposal())).build();
            case PushToJira.Conflict c -> Response.status(Response.Status.CONFLICT).type(Problem.MEDIA_TYPE)
                    .entity(ConflictProblem.of(c)).build();
        };
    }

    private static ConflictResolution resolution(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(ConflictResolution.values()).filter(r -> r.name().equals(value)).findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.VALIDATION_FAILED,
                        "Choix inconnu : OVERWRITE, TAKE_JIRA ou AI_MERGE."));
    }
}
