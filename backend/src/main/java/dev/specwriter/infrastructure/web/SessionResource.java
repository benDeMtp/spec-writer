package dev.specwriter.infrastructure.web;

import dev.specwriter.application.session.AnswerQuestion;
import dev.specwriter.application.session.ConcludeQuestionnaire;
import dev.specwriter.application.session.ManageSessions;
import dev.specwriter.application.session.SessionSnapshot;
import dev.specwriter.application.session.StartSession;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.session.Session;
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
import java.util.List;
import java.util.UUID;

/** {@code /api/sessions} : besoin, questionnaire et conclusion (contracts/openapi.yaml, US2). */
@Path("/api/sessions")
@Produces(MediaType.APPLICATION_JSON)
public class SessionResource {

    public record CreateRequest(String need, String name) {
    }

    public record AnswerRequest(String value, Boolean skip) {
    }

    public record RenameRequest(String name) {
    }

    @Inject
    SessionRepository sessions;
    @Inject
    SpecificationRepository versions;
    @Inject
    StartSession startSession;
    @Inject
    AnswerQuestion answerQuestion;
    @Inject
    ConcludeQuestionnaire concludeQuestionnaire;
    @Inject
    ManageSessions manageSessions;

    @GET
    public List<SessionViews.SessionSummary> list() {
        return manageSessions.list().stream().map(SessionViews::summary).toList();
    }

    @PATCH
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public SessionViews.SessionSummary rename(@PathParam("id") String id, RenameRequest request) {
        UUID sessionId = Ids.parse(id);
        Session renamed = manageSessions.rename(sessionId, request == null ? null : request.name());
        return SessionViews.summary(renamed, versions.lastNumber(sessionId));
    }

    @DELETE
    @Path("/{id}")
    public void delete(@PathParam("id") String id) {
        manageSessions.delete(Ids.parse(id));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(CreateRequest request) {
        if (request == null) {
            throw new DomainException(ErrorCode.NEED_TOO_SHORT);
        }
        SessionSnapshot snapshot = startSession.run(request.need(), request.name());
        return Response.status(Response.Status.CREATED).entity(SessionViews.detail(snapshot, 0)).build();
    }

    @GET
    @Path("/{id}")
    public SessionViews.SessionDetail get(@PathParam("id") String id) {
        UUID sessionId = Ids.parse(id);
        Session session = sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        return SessionViews.detail(new SessionSnapshot(session, sessions.questionnaire(sessionId).orElseThrow(), List.of()),
                versions.lastNumber(sessionId));
    }

    @PUT
    @Path("/{id}/questions/{questionId}/answer")
    @Consumes(MediaType.APPLICATION_JSON)
    public SessionViews.SessionDetail answer(@PathParam("id") String id, @PathParam("questionId") String questionId,
            AnswerRequest request) {
        boolean skip = request != null && Boolean.TRUE.equals(request.skip());
        if (request == null || !skip && request.value() == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Saisissez une réponse, ou passez la question.");
        }
        UUID sessionId = Ids.parse(id);
        SessionSnapshot snapshot = answerQuestion.run(sessionId, Ids.parse(questionId), request.value(), skip);
        return SessionViews.detail(snapshot, versions.lastNumber(sessionId));
    }

    @POST
    @Path("/{id}/conclude")
    public SessionViews.SessionDetail conclude(@PathParam("id") String id) {
        UUID sessionId = Ids.parse(id);
        return SessionViews.detail(concludeQuestionnaire.run(sessionId), versions.lastNumber(sessionId));
    }
}
