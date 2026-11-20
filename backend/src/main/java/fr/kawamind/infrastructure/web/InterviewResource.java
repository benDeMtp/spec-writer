package fr.kawamind.infrastructure.web;

import fr.kawamind.application.interview.InterviewRevision;
import fr.kawamind.application.interview.PrepareInterview;
import fr.kawamind.application.project.CurrentProject;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.application.interview.RunInterview;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.interview.InterviewPoint;
import fr.kawamind.domain.specification.Anchor;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/** Séances de relecture avec les sachants (021, contracts/rest-api.md). */
@Path("/api/interviews")
@Produces(MediaType.APPLICATION_JSON)
public class InterviewResource {

    public record AnchorView(String element, Integer step, boolean alternative) {
        static AnchorView of(Anchor a) {
            return a == null ? null : new AnchorView(a.element(), a.step(), a.alternative());
        }

        Anchor anchor() {
            return element == null || element.isBlank() ? null : new Anchor(element, step, alternative);
        }
    }

    public record SourceView(String kind, String ref) {
    }

    public record PointView(UUID id, UUID sessionId, AnchorView anchor, String origin, String originCode, String text,
            String outcome, String owner, LocalDate due, SourceView source, Instant createdAt, Instant updatedAt) {
        static PointView of(InterviewPoint p) {
            return new PointView(p.id(), p.sessionId(), AnchorView.of(p.anchor()), p.origin().name(), p.originCode(),
                    p.text(), p.outcome() == null ? null : p.outcome().name(), p.owner(), p.due(),
                    p.source() == null ? null : new SourceView(p.source().kind(), p.source().ref()), p.createdAt(),
                    p.updatedAt());
        }
    }

    public record PointInput(UUID sessionId, AnchorView anchor, String origin, String originCode, String text,
            String outcome, String owner, String due, SourceView source) {
    }

    public record ItemView(String origin, String code, String statement, PointView point, boolean pending) {
    }

    public record StopView(UUID sessionId, AnchorView anchor, String kind, String label, String text, List<ItemView> items) {
    }

    public record SpecView(UUID sessionId, String name, int rank, Integer version, int latestVersion, List<String> excluded,
            Integer appliedVersion, boolean revisionAvailable, String revisionUnavailableReason) {
    }

    public record CursorView(UUID sessionId, AnchorView anchor) {
    }

    public record InterviewDetail(UUID id, String state, List<String> participants, Instant createdAt, Instant startedAt,
            Instant finishedAt, CursorView cursor, List<SpecView> specs, List<StopView> stops,
            fr.kawamind.domain.interview.Walkthrough.Counts counts) {
    }

    public record SpecName(UUID sessionId, String name) {
    }

    public record InterviewSummary(UUID id, String state, List<String> participants, Instant createdAt,
            Instant startedAt, Instant finishedAt, List<SpecName> specifications, int points) {
    }

    public record StartInput(List<String> participants) {
    }

    public record CursorInput(UUID sessionId, String element, Integer step, boolean alternative) {
    }

    @Inject
    RunInterview run;

    @Inject
    InterviewViews views;

    @Inject
    InterviewRevision revision;

    @Inject
    PrepareInterview prepare;

    @Inject
    fr.kawamind.application.interview.SourceUpdates sourceUpdates;

    @Inject
    ProjectRepository projects;

    /** Une spécification de la séance préparée, avec ses points écartés. */
    public record SpecChoice(UUID sessionId, List<String> excluded) {
    }

    public record PrepareInput(List<SpecChoice> specs, List<String> participants) {
    }

    /** Séances du projet courant dans cet état ; seules les séances préparées se listent ainsi (US5). */
    @GET
    public List<InterviewSummary> list(@QueryParam("state") String state) {
        if (!"PREPARED".equals(state)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        return prepare.preparedFor(CurrentProject.resolve(projects).id()).stream().map(views::summary).toList();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(PrepareInput input) {
        RunInterview.View v = prepare.prepare(CurrentProject.resolve(projects).id(), choices(input),
                input == null ? List.of() : input.participants());
        return Response.status(Response.Status.CREATED).entity(views.detail(v)).build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public InterviewDetail update(@PathParam("id") String id, PrepareInput input) {
        return views.detail(prepare.update(Ids.parse(id), choices(input), input == null ? null : input.participants()));
    }

    @DELETE
    @Path("/{id}")
    public void delete(@PathParam("id") String id) {
        prepare.delete(Ids.parse(id));
    }

    @POST
    @Path("/{id}/start")
    @Consumes(MediaType.APPLICATION_JSON)
    public InterviewDetail start(@PathParam("id") String id, StartInput input) {
        return views.detail(prepare.start(Ids.parse(id), input == null ? null : input.participants()));
    }

    private static List<PrepareInterview.Choice> choices(PrepareInput input) {
        if (input == null || input.specs() == null) {
            return List.of();
        }
        return input.specs().stream().filter(java.util.Objects::nonNull)
                .map(c -> new PrepareInterview.Choice(c.sessionId(),
                        c.excluded() == null ? java.util.Set.of() : java.util.Set.copyOf(c.excluded())))
                .toList();
    }

    @GET
    @Path("/{id}")
    public InterviewDetail get(@PathParam("id") String id) {
        return views.detail(run.view(Ids.parse(id)));
    }

    @PUT
    @Path("/{id}/cursor")
    @Consumes(MediaType.APPLICATION_JSON)
    public void cursor(@PathParam("id") String id, CursorInput input) {
        if (input == null || input.sessionId() == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        run.moveCursor(Ids.parse(id), input.sessionId(), input.element() == null ? null
                : new Anchor(input.element(), input.step(), input.alternative()));
    }

    @POST
    @Path("/{id}/points")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addPoint(@PathParam("id") String id, PointInput input) {
        InterviewPoint p = run.savePoint(Ids.parse(id), null, known(input(input)));
        return Response.status(Response.Status.CREATED).entity(PointView.of(p)).build();
    }

    @PUT
    @Path("/{id}/points/{pid}")
    @Consumes(MediaType.APPLICATION_JSON)
    public PointView updatePoint(@PathParam("id") String id, @PathParam("pid") String pid, PointInput input) {
        return PointView.of(run.savePoint(Ids.parse(id), Ids.parse(pid), known(input(input))));
    }

    @DELETE
    @Path("/{id}/points/{pid}")
    public void deletePoint(@PathParam("id") String id, @PathParam("pid") String pid) {
        run.deletePoint(Ids.parse(id), Ids.parse(pid));
    }

    @POST
    @Path("/{id}/finish")
    public InterviewDetail finish(@PathParam("id") String id) {
        return views.detail(run.finish(Ids.parse(id)));
    }

    public record ReportView(String html, String text) {
    }

    @GET
    @Path("/{id}/report")
    public ReportView report(@PathParam("id") String id) {
        var report = run.report(Ids.parse(id));
        return new ReportView(report.html(), report.text());
    }

    /** La rédaction lancée : le tour se suit sur la discussion de la spécification. */
    public record RevisionStarted(UUID conversationId, UUID turnId) {
    }

    /** « Proposer la nouvelle version » (FR-013) : 202, la rédaction de la 011 est en cours. */
    @POST
    @Path("/{id}/specs/{sid}/revision")
    public Response revise(@PathParam("id") String id, @PathParam("sid") String sid) {
        InterviewRevision.Started started = revision.propose(Ids.parse(id), Ids.parse(sid));
        return Response.accepted(new RevisionStarted(started.conversationId(), started.turnId())).build();
    }

    /** Une source désignée en séance est dans la connaissance du projet (US6, FR-028). */
    private RunInterview.PointInput known(RunInterview.PointInput in) {
        if (in.source() != null) {
            sourceUpdates.requireKnown(in.sessionId(), in.source());
        }
        return in;
    }

    static RunInterview.PointInput input(PointInput in) {
        if (in == null || in.sessionId() == null || in.origin() == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        return new RunInterview.PointInput(in.sessionId(), in.anchor() == null ? null : in.anchor().anchor(),
                parse(InterviewPoint.Origin.class, in.origin()), in.originCode(), in.text(),
                in.outcome() == null ? null : parse(InterviewPoint.Outcome.class, in.outcome()), in.owner(), date(in.due()),
                in.source() == null || in.source().ref() == null ? null
                        : new InterviewPoint.DesignatedSource(in.source().kind(), in.source().ref()));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String raw) {
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private static LocalDate date(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "L'échéance n'est pas une date valide.");
        }
    }
}
