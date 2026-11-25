package fr.kawamind.infrastructure.web;

import fr.kawamind.application.interview.RunInterview;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.infrastructure.web.InterviewResource.InterviewSummary;
import fr.kawamind.infrastructure.web.InterviewResource.StartInput;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

/** Séances d'une spécification : « Faire le point » et séances passées (021, contracts/rest-api.md). */
@Path("/api/sessions/{sid}/interviews")
@Produces(MediaType.APPLICATION_JSON)
public class SessionInterviewResource {

    @Inject
    RunInterview run;

    @Inject
    SessionRepository sessions;

    @Inject
    InterviewViews views;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response start(@PathParam("sid") String sid, StartInput input) {
        RunInterview.View v = run.startForSpecification(Ids.parse(sid),
                input == null || input.participants() == null ? List.of() : input.participants());
        return Response.status(Response.Status.CREATED).entity(views.detail(v)).build();
    }

    @GET
    public List<InterviewSummary> forSpecification(@PathParam("sid") String sid) {
        UUID sessionId = Ids.parse(sid);
        sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        return run.forSpecification(sessionId).stream().map(views::summary).toList();
    }

}
