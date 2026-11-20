package fr.kawamind.application.interview;

import fr.kawamind.application.specification.OperationRegistry;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.interview.Interview;
import fr.kawamind.domain.interview.InterviewPoint;
import fr.kawamind.domain.interview.InterviewSpec;
import fr.kawamind.domain.interview.PendingPoints;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.IdGenerator;
import fr.kawamind.domain.port.InterviewRepository;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.port.SpecificationRepository;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.specification.SpecificationContent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Préparer une séance de plusieurs spécifications (021, US5, FR-022 à FR-025) : choix et ordre des
 * spécifications, points écartés, participants ; la séance préparée attend « Démarrer », qui fixe
 * la version relue de chacune. Aucun appel au modèle.
 */
public class PrepareInterview {

    /** Une spécification choisie, avec les codes de ses points écartés de cette séance. */
    public record Choice(UUID sessionId, Set<String> excluded) {

        public Choice {
            excluded = excluded == null ? Set.of() : Set.copyOf(excluded);
        }
    }

    private final InterviewRepository interviews;
    private final SessionRepository sessions;
    private final SpecificationRepository versions;
    private final OperationRegistry operations;
    private final RunInterview run;
    private final ClockPort clock;
    private final IdGenerator ids;

    public PrepareInterview(InterviewRepository interviews, SessionRepository sessions, SpecificationRepository versions,
            OperationRegistry operations, RunInterview run, ClockPort clock, IdGenerator ids) {
        this.interviews = interviews;
        this.sessions = sessions;
        this.versions = versions;
        this.operations = operations;
        this.run = run;
        this.clock = clock;
        this.ids = ids;
    }

    public synchronized RunInterview.View prepare(UUID projectId, List<Choice> choices, List<String> participants) {
        Interview interview = Interview.prepare(ids.next(), projectId, participants == null ? List.of() : participants,
                clock.now());
        interviews.save(interview, specs(interview.id(), choices));
        return run.view(interview.id());
    }

    public synchronized RunInterview.View update(UUID interviewId, List<Choice> choices, List<String> participants) {
        Interview interview = prepared(interviewId);
        interviews.save(interview.withParticipants(participants == null ? interview.participants() : participants),
                specs(interviewId, choices));
        return run.view(interviewId);
    }

    public synchronized void delete(UUID interviewId) {
        prepared(interviewId);
        interviews.delete(interviewId);
    }

    /**
     * « Démarrer » : la séance passe en cours, sur la dernière version de chaque spécification ;
     * refusé si l'une a déjà une séance en cours ou une rédaction en cours (FR-007).
     */
    public synchronized RunInterview.View start(UUID interviewId, List<String> participants) {
        Interview interview = prepared(interviewId);
        List<InterviewSpec> fixed = new ArrayList<>();
        for (InterviewSpec spec : interviews.specs(interviewId)) {
            String name = sessions.find(spec.sessionId()).map(Session::name).orElse("");
            if (interviews.inProgress(spec.sessionId()).isPresent()) {
                throw new DomainException(ErrorCode.INTERVIEW_BUSY,
                        "« " + name + " » a déjà une séance en cours : reprenez-la ou terminez-la.");
            }
            if (operations.running(spec.sessionId())) {
                throw new DomainException(ErrorCode.OPERATION_IN_PROGRESS,
                        "Une rédaction est en cours sur « " + name + " » : attendez qu'elle finisse.");
            }
            fixed.add(spec.withVersion(versions.lastNumber(spec.sessionId())));
        }
        if (fixed.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Cette séance n'a plus de spécification.");
        }
        Interview started = interview.start(clock.now());
        interviews.save(participants == null || participants.isEmpty() ? started : started.withParticipants(participants),
                fixed);
        return run.view(interviewId);
    }

    /**
     * Séances préparées du projet, les plus récentes d'abord ; une séance dont toutes les
     * spécifications ont été supprimées disparaît.
     */
    public synchronized List<Interview> preparedFor(UUID projectId) {
        List<Interview> out = new ArrayList<>();
        for (Interview i : interviews.byState(projectId, Interview.State.PREPARED)) {
            if (interviews.specs(i.id()).isEmpty()) {
                interviews.delete(i.id());
            } else {
                out.add(i);
            }
        }
        return out;
    }

    /**
     * Points à traiter de la spécification (FR-025) ; vide sans version. Les points tranchés d'une
     * séance terminée dont la révision n'est pas appliquée ne comptent plus.
     */
    public Optional<PendingPoints> pending(UUID sessionId) {
        int latest = versions.lastNumber(sessionId);
        if (latest < 1) {
            return Optional.empty();
        }
        Optional<SpecificationContent> content = versions.find(sessionId, latest).map(v -> v.content());
        if (content.isEmpty()) {
            return Optional.empty();
        }
        Set<String> decided = new HashSet<>();
        for (Interview i : interviews.forSession(sessionId)) {
            if (i.state() != Interview.State.FINISHED) {
                continue;
            }
            boolean applied = interviews.specs(i.id()).stream()
                    .anyMatch(s -> s.sessionId().equals(sessionId) && s.appliedVersion() != null);
            if (!applied) {
                interviews.points(i.id()).stream()
                        .filter(p -> p.sessionId().equals(sessionId) && p.outcome() != null && p.originCode() != null)
                        .map(InterviewPoint::originCode).forEach(decided::add);
            }
        }
        return Optional.of(PendingPoints.of(content.get(), decided));
    }

    private Interview prepared(UUID interviewId) {
        Interview interview = interviews.find(interviewId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        interview.requirePrepared();
        return interview;
    }

    /** Spécifications dans l'ordre choisi, sans doublon ; les points écartés sont des codes de leur dernière version. */
    private List<InterviewSpec> specs(UUID interviewId, List<Choice> choices) {
        if (choices == null || choices.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Choisissez au moins une spécification.");
        }
        List<InterviewSpec> out = new ArrayList<>();
        Set<UUID> seen = new LinkedHashSet<>();
        for (Choice c : choices) {
            if (c == null || c.sessionId() == null || !seen.add(c.sessionId())) {
                continue;
            }
            sessions.find(c.sessionId()).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
            int latest = versions.lastNumber(c.sessionId());
            SpecificationContent content = versions.find(c.sessionId(), latest).map(v -> v.content())
                    .orElseThrow(() -> new DomainException(ErrorCode.VALIDATION_FAILED,
                            "Une spécification sans version ne se relit pas en séance."));
            Set<String> codes = new HashSet<>();
            content.assumptions().forEach(a -> codes.add(a.code()));
            content.openQuestions().forEach(q -> codes.add(q.code()));
            content.sourceDeviations().forEach(d -> codes.add(d.code()));
            if (!codes.containsAll(c.excluded())) {
                throw new DomainException(ErrorCode.VALIDATION_FAILED,
                        "Un point écarté n'existe pas dans la dernière version.");
            }
            out.add(new InterviewSpec(interviewId, c.sessionId(), out.size() + 1, null, c.excluded(), null));
        }
        return out;
    }
}
