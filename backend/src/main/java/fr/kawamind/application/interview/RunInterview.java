package fr.kawamind.application.interview;

import fr.kawamind.application.specification.OperationRegistry;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.interview.Interview;
import fr.kawamind.domain.interview.InterviewPoint;
import fr.kawamind.domain.interview.InterviewReport;
import fr.kawamind.domain.interview.InterviewSpec;
import fr.kawamind.domain.interview.Walkthrough;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.IdGenerator;
import fr.kawamind.domain.port.InterviewRepository;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.port.SpecificationRepository;
import fr.kawamind.domain.session.Language;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.specification.Anchor;
import fr.kawamind.domain.specification.Assumption;
import fr.kawamind.domain.specification.OpenQuestion;
import fr.kawamind.domain.specification.SourceDeviation;
import fr.kawamind.domain.specification.SpecificationContent;
import fr.kawamind.domain.specification.SpecificationVersion;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Mener une séance (021, US1, US2) : « Faire le point » sur une spécification, parcours, points
 * notés au fil de l'eau, curseur pour reprendre, fin de séance. Aucun appel au modèle (SC-001).
 */
public class RunInterview {

    /** Ce qui est saisi pour un point ; {@code originCode} : code de l'hypothèse, de l'écart ou du point ouvert. */
    public record PointInput(UUID sessionId, Anchor anchor, InterviewPoint.Origin origin, String originCode, String text,
            InterviewPoint.Outcome outcome, String owner, LocalDate due, InterviewPoint.DesignatedSource source,
            String quote) {

        /** Sans passage cité. */
        public PointInput(UUID sessionId, Anchor anchor, InterviewPoint.Origin origin, String originCode, String text,
                InterviewPoint.Outcome outcome, String owner, LocalDate due, InterviewPoint.DesignatedSource source) {
            this(sessionId, anchor, origin, originCode, text, outcome, owner, due, source, null);
        }
    }

    /** Une spécification de la séance : sa session, sa version relue, sa dernière version. */
    public record Spec(InterviewSpec spec, Session session, SpecificationVersion read, int latestVersion) {
    }

    /** La séance, ses spécifications, son parcours et ses points. */
    public record View(Interview interview, List<Spec> specs, Walkthrough walkthrough, List<InterviewPoint> points) {
    }

    private final InterviewRepository interviews;
    private final SessionRepository sessions;
    private final SpecificationRepository versions;
    private final OperationRegistry operations;
    private final ClockPort clock;
    private final IdGenerator ids;

    public RunInterview(InterviewRepository interviews, SessionRepository sessions, SpecificationRepository versions,
            OperationRegistry operations, ClockPort clock, IdGenerator ids) {
        this.interviews = interviews;
        this.sessions = sessions;
        this.versions = versions;
        this.operations = operations;
        this.clock = clock;
        this.ids = ids;
    }

    /**
     * « Faire le point » (FR-001) : une séance d'une spécification, en cours dès sa création, sur sa
     * dernière version. Sans participants donnés, ceux de la séance précédente sont repris.
     */
    public synchronized View startForSpecification(UUID sessionId, List<String> participants) {
        Session session = sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (operations.running(sessionId)) {
            throw new DomainException(ErrorCode.OPERATION_IN_PROGRESS);
        }
        if (interviews.inProgress(sessionId).isPresent()) {
            throw new DomainException(ErrorCode.INTERVIEW_BUSY);
        }
        int latest = versions.lastNumber(sessionId);
        if (latest < 1) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Cette spécification n'a pas encore de version à relire.");
        }
        List<String> names = participants == null || participants.isEmpty() ? previousParticipants(sessionId) : participants;
        Interview interview = Interview.startNow(ids.next(), session.projectId(), names, clock.now());
        interviews.save(interview, List.of(new InterviewSpec(interview.id(), sessionId, 1, latest, Set.of(), null)));
        return view(interview.id());
    }

    /** Participants de la dernière séance de cette spécification, proposés à la suivante. */
    public List<String> previousParticipants(UUID sessionId) {
        return interviews.forSession(sessionId).stream().findFirst().map(Interview::participants).orElse(List.of());
    }

    public View view(UUID interviewId) {
        Interview interview = interview(interviewId);
        List<InterviewPoint> points = interviews.points(interviewId);
        List<Spec> specs = new ArrayList<>();
        List<Walkthrough.SpecInput> inputs = new ArrayList<>();
        Language language = null;
        for (InterviewSpec spec : interviews.specs(interviewId)) {
            Optional<Session> session = sessions.find(spec.sessionId());
            int latest = versions.lastNumber(spec.sessionId());
            int number = spec.version() == null ? latest : spec.version();
            Optional<SpecificationVersion> read = versions.find(spec.sessionId(), number);
            if (session.isEmpty() || read.isEmpty()) {
                continue;
            }
            language = language == null ? session.get().language() : language;
            specs.add(new Spec(spec, session.get(), read.get(), latest));
            inputs.add(new Walkthrough.SpecInput(spec.sessionId(), read.get().content(), spec.excluded(),
                    points.stream().filter(p -> p.sessionId().equals(spec.sessionId())).toList()));
        }
        Walkthrough walkthrough = Walkthrough.of(inputs, language == null ? Language.FR : language);
        return new View(interview, List.copyOf(specs), walkthrough, points);
    }

    public void moveCursor(UUID interviewId, UUID sessionId, Anchor anchor) {
        Interview interview = interview(interviewId);
        interview.requireInProgress();
        spec(interviewId, sessionId);
        interviews.save(interview.withCursor(new Interview.Cursor(sessionId, anchor)));
    }

    /**
     * Enregistre un point (FR-004 à FR-006). Sans identifiant, un point de la spécification déjà
     * répondu dans cette séance est mis à jour plutôt que dupliqué.
     */
    public synchronized InterviewPoint savePoint(UUID interviewId, UUID pointId, PointInput input) {
        Interview interview = interview(interviewId);
        interview.requireInProgress();
        InterviewSpec spec = spec(interviewId, input.sessionId());
        SpecificationContent content = read(spec).content();
        if (input.anchor() != null && !input.anchor().resolves(content)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Cet élément n'existe pas dans la version relue.");
        }
        if (input.origin() != null && input.origin().fromSpecification() && !codes(content).contains(input.originCode())) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Ce point n'existe pas dans la version relue.");
        }
        Optional<InterviewPoint> existing = pointId != null
                ? interviews.point(pointId).filter(p -> p.interviewId().equals(interviewId))
                : sameOrigin(interviewId, input);
        if (pointId != null && existing.isEmpty()) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
        if (existing.isEmpty()) {
            InterviewPoint.requireRoom(interviews.countPoints(interviewId, input.sessionId()));
        }
        Instant now = clock.now();
        InterviewPoint point = new InterviewPoint(existing.map(InterviewPoint::id).orElseGet(ids::next), interviewId,
                input.sessionId(), input.anchor(), existing.map(InterviewPoint::origin).orElse(input.origin()),
                existing.map(InterviewPoint::originCode).orElse(input.originCode()), input.text(), input.outcome(),
                input.owner(), input.due(), input.source(), existing.map(InterviewPoint::createdAt).orElse(now), now,
                input.quote());
        interviews.savePoint(point);
        return point;
    }

    /** Supprime une remarque ou une question ; la réponse à un point de la spécification se modifie. */
    public void deletePoint(UUID interviewId, UUID pointId) {
        interview(interviewId).requireInProgress();
        InterviewPoint point = interviews.point(pointId).filter(p -> p.interviewId().equals(interviewId))
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (point.origin().fromSpecification()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "La réponse à un point de la spécification se modifie, elle ne se supprime pas.");
        }
        interviews.deletePoint(pointId);
    }

    /**
     * « Terminer » (FR-009) : la séance est close, ses spécifications sont libérées. Sans aucun point
     * noté, elle est supprimée : vide, l'écran revient à la spécification sans récapitulatif.
     */
    public synchronized Optional<View> finish(UUID interviewId) {
        Interview interview = interview(interviewId);
        Interview finished = interview.finish(clock.now());
        // une relecture terminée sans rien noter n'est pas une séance : rien n'est gardé
        if (interviews.points(interviewId).isEmpty()) {
            interviews.delete(interviewId);
            return Optional.empty();
        }
        interviews.save(finished);
        return Optional.of(view(interviewId));
    }

    /**
     * Compte rendu de la séance (FR-010, R7), sans modèle, regroupé par spécification ; daté du
     * démarrage de la séance, dans le fuseau du poste.
     */
    public InterviewReport.Report report(UUID interviewId) {
        View v = view(interviewId);
        Instant at = v.interview().startedAt() == null ? v.interview().createdAt() : v.interview().startedAt();
        LocalDate date = at.atZone(ZoneId.systemDefault()).toLocalDate();
        List<InterviewReport.Section> sections = v.specs().stream()
                .map(s -> {
                    List<InterviewPoint> points = v.points().stream()
                            .filter(p -> p.sessionId().equals(s.spec().sessionId())).toList();
                    // sources désignées en séance (US6) : à mettre à jour à la main
                    List<InterviewReport.SourceLine> sources = points.stream().filter(p -> p.source() != null)
                            .map(p -> new InterviewReport.SourceLine(p.source().ref(), p.text())).toList();
                    return new InterviewReport.Section(s.session().name(), s.read().content(), points, sources);
                })
                .toList();
        Language language = v.specs().isEmpty() ? Language.FR : v.specs().getFirst().session().language();
        return InterviewReport.render(date, v.interview().participants(), sections, language);
    }

    /** Séances de cette spécification, les plus récentes d'abord (FR-012). */
    public List<Interview> forSpecification(UUID sessionId) {
        return interviews.forSession(sessionId);
    }

    private Optional<InterviewPoint> sameOrigin(UUID interviewId, PointInput input) {
        if (input.origin() == null || !input.origin().fromSpecification()) {
            return Optional.empty();
        }
        return interviews.points(interviewId).stream()
                .filter(p -> p.sessionId().equals(input.sessionId()) && p.origin() == input.origin()
                        && p.originCode().equals(input.originCode()))
                .findFirst();
    }

    private Interview interview(UUID interviewId) {
        return interviews.find(interviewId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    private InterviewSpec spec(UUID interviewId, UUID sessionId) {
        return interviews.specs(interviewId).stream().filter(s -> s.sessionId().equals(sessionId)).findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    private SpecificationVersion read(InterviewSpec spec) {
        int number = spec.version() == null ? versions.lastNumber(spec.sessionId()) : spec.version();
        return versions.find(spec.sessionId(), number).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    private static Set<String> codes(SpecificationContent content) {
        Set<String> codes = new java.util.HashSet<>();
        content.assumptions().stream().map(Assumption::code).forEach(codes::add);
        content.sourceDeviations().stream().map(SourceDeviation::code).forEach(codes::add);
        content.openQuestions().stream().map(OpenQuestion::code).forEach(codes::add);
        return codes;
    }
}
