package dev.specwriter.infrastructure.web;

import dev.specwriter.application.project.MoveSession;
import dev.specwriter.application.session.AnswerQuestion;
import dev.specwriter.application.session.ChangeStatus;
import dev.specwriter.application.session.EvolveSpecification;
import dev.specwriter.application.session.ConcludeQuestionnaire;
import dev.specwriter.application.session.ManageSessions;
import dev.specwriter.application.session.SessionSnapshot;
import dev.specwriter.application.session.StartSession;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.port.LinkedTicketRepository;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.session.Implementation;
import dev.specwriter.domain.session.ImplementationStatus;
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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** {@code /api/sessions} : besoin, questionnaire et conclusion (contracts/openapi.yaml, US2). */
@Path("/api/sessions")
@Produces(MediaType.APPLICATION_JSON)
public class SessionResource {

    /** {@code attachmentIds} : documents joints sur l'écran du besoin, dans l'ordre d'ajout (009). */
    public record CreateRequest(String need, String name, List<String> attachmentIds) {
    }

    public record AnswerRequest(String value, Boolean skip) {
    }

    public record RenameRequest(String name) {
    }

    public record MoveRequest(String projectId) {
    }

    public record StatusRequest(String status, Boolean confirmUnlock) {
    }

    @Inject
    SessionRepository sessions;
    @Inject
    SpecificationRepository versions;
    @Inject
    LinkedTicketRepository tickets;
    @Inject
    StartSession startSession;
    @Inject
    AnswerQuestion answerQuestion;
    @Inject
    ConcludeQuestionnaire concludeQuestionnaire;
    @Inject
    ManageSessions manageSessions;
    @Inject
    MoveSession moveSession;
    @Inject
    ChangeStatus changeStatus;
    @Inject
    EvolveSpecification evolveSpecification;

    @GET
    public List<SessionViews.SessionSummary> list() {
        Map<UUID, LinkedJiraTicket> linked = tickets.all().stream()
                .collect(Collectors.toMap(LinkedJiraTicket::sessionId, Function.identity()));
        return manageSessions.list().stream().map(e -> SessionViews.summary(e, linked.get(e.session().id()))).toList();
    }

    @PATCH
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public SessionViews.SessionSummary rename(@PathParam("id") String id, RenameRequest request) {
        UUID sessionId = Ids.parse(id);
        Session renamed = manageSessions.rename(sessionId, request == null ? null : request.name());
        return SessionViews.summary(renamed, versions.lastNumber(sessionId), ticket(sessionId),
                implementation(sessionId).status());
    }

    /**
     * {@code discussions} : {@code delete} (par défaut) supprime les discussions rattachées,
     * {@code keep} les garde sans spécification (011, research.md R7).
     */
    @DELETE
    @Path("/{id}")
    public void delete(@PathParam("id") String id, @QueryParam("discussions") String discussions) {
        if (discussions != null && !"delete".equals(discussions) && !"keep".equals(discussions)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "discussions : delete ou keep.");
        }
        manageSessions.delete(Ids.parse(id), "keep".equals(discussions));
    }

    public record DiscussionCount(int count) {
    }

    /** Discussions rattachées : la confirmation de suppression propose de les garder (011). */
    @GET
    @Path("/{id}/discussions/count")
    public DiscussionCount discussions(@PathParam("id") String id) {
        return new DiscussionCount(manageSessions.discussions(Ids.parse(id)));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(CreateRequest request) {
        if (request == null) {
            throw new DomainException(ErrorCode.NEED_TOO_SHORT);
        }
        List<UUID> attachments = request.attachmentIds() == null ? List.of()
                : request.attachmentIds().stream().map(Ids::draft).toList();
        SessionSnapshot snapshot = startSession.run(request.need(), request.name(), attachments);
        return Response.status(Response.Status.CREATED).entity(SessionViews.detail(snapshot, 0, null)).build();
    }

    @GET
    @Path("/{id}")
    public SessionViews.SessionDetail get(@PathParam("id") String id) {
        UUID sessionId = Ids.parse(id);
        Session session = sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        return SessionViews.detail(new SessionSnapshot(session, sessions.questionnaire(sessionId).orElseThrow(), List.of()),
                versions.lastNumber(sessionId), ticket(sessionId), implementation(sessionId));
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
        return SessionViews.detail(snapshot, versions.lastNumber(sessionId), ticket(sessionId), implementation(sessionId));
    }

    /** Statut d'implémentation (FR-026) ; quitter « implémentée » exige {@code confirmUnlock}. */
    @PUT
    @Path("/{id}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    public SessionViews.ImplementationView status(@PathParam("id") String id, StatusRequest request) {
        if (request == null || request.status() == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Le statut est obligatoire.");
        }
        ImplementationStatus target;
        try {
            target = ImplementationStatus.valueOf(request.status());
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Statut inconnu.");
        }
        return SessionViews.ImplementationView.of(
                changeStatus.run(Ids.parse(id), target, Boolean.TRUE.equals(request.confirmUnlock())));
    }

    /** Évolution d'une spécification : nouvelle session en brouillon, dans le même projet (FR-026b). */
    @POST
    @Path("/{id}/evolutions")
    public Response evolve(@PathParam("id") String id) {
        SessionSnapshot evolved = evolveSpecification.run(Ids.parse(id));
        return Response.status(Response.Status.CREATED)
                .entity(SessionViews.detail(evolved, 0, null, implementation(evolved.session().id()))).build();
    }

    /** Déplace la spécification vers un autre projet, avec ses versions (FR-004 de 002). */
    @PUT
    @Path("/{id}/project")
    @Consumes(MediaType.APPLICATION_JSON)
    public void move(@PathParam("id") String id, MoveRequest request) {
        UUID projectId;
        try {
            projectId = Ids.parse(request == null ? null : request.projectId());
        } catch (DomainException e) {
            throw new DomainException(ErrorCode.PROJECT_NOT_FOUND);
        }
        moveSession.run(Ids.parse(id), projectId);
    }

    @POST
    @Path("/{id}/conclude")
    public SessionViews.SessionDetail conclude(@PathParam("id") String id) {
        UUID sessionId = Ids.parse(id);
        return SessionViews.detail(concludeQuestionnaire.run(sessionId), versions.lastNumber(sessionId), ticket(sessionId),
                implementation(sessionId));
    }

    private Implementation implementation(UUID sessionId) {
        return sessions.implementation(sessionId).orElseGet(Implementation::initial);
    }

    private LinkedJiraTicket ticket(UUID sessionId) {
        return tickets.findBySession(sessionId).orElse(null);
    }
}
