package fr.kawamind.application.interview;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.interview.InterviewPoint;
import fr.kawamind.domain.interview.InterviewSpec;
import fr.kawamind.domain.interview.SourceUpdate;
import fr.kawamind.domain.knowledge.KnowledgeItem;
import fr.kawamind.domain.knowledge.SourceOrigin;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.IdGenerator;
import fr.kawamind.domain.port.InterviewRepository;
import fr.kawamind.domain.port.KnowledgeIndex;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.port.SourceUpdateRepository;
import fr.kawamind.domain.port.SpecificationRepository;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.specification.Assumption;
import fr.kawamind.domain.specification.OpenQuestion;
import fr.kawamind.domain.specification.SourceDeviation;
import fr.kawamind.domain.specification.SourceRef;
import fr.kawamind.domain.specification.SpecificationContent;
import fr.kawamind.domain.specification.UseCase;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * « À reporter » (021, US6, research.md R8) : créés à l'application de la révision d'une séance pour
 * chaque décision qui touche une source de vérité ; signalés quand la source change ; clos à la main.
 * Rien n'est jamais écrit dans les sources (FR-030).
 */
public class SourceUpdates {

    /** Un « à reporter » ouvert et l'état de sa source : modifiée ou retirée depuis la décision. */
    public record Line(SourceUpdate update, boolean sourceChanged) {
    }

    private final SourceUpdateRepository updates;
    private final InterviewRepository interviews;
    private final SessionRepository sessions;
    private final SpecificationRepository versions;
    private final KnowledgeIndex knowledge;
    private final ClockPort clock;
    private final IdGenerator ids;

    public SourceUpdates(SourceUpdateRepository updates, InterviewRepository interviews, SessionRepository sessions,
            SpecificationRepository versions, KnowledgeIndex knowledge, ClockPort clock, IdGenerator ids) {
        this.updates = updates;
        this.interviews = interviews;
        this.sessions = sessions;
        this.versions = versions;
        this.knowledge = knowledge;
        this.clock = clock;
        this.ids = ids;
    }

    /**
     * À l'application de la révision : une décision « à modifier » ou « à retirer » sur un use case
     * qui cite une source de vérité, un écart tranché, et chaque source désignée à la main.
     */
    public synchronized List<SourceUpdate> recordFor(UUID interviewId, UUID sessionId) {
        Session session = sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        Optional<InterviewSpec> spec = interviews.specs(interviewId).stream()
                .filter(s -> s.sessionId().equals(sessionId)).findFirst();
        if (spec.isEmpty()) {
            return List.of();
        }
        int number = spec.get().version() == null ? versions.lastNumber(sessionId) : spec.get().version();
        SpecificationContent read = versions.find(sessionId, number).map(v -> v.content()).orElse(null);
        List<SourceUpdate> created = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (InterviewPoint p : interviews.points(interviewId)) {
            // un point reporté n'est pas une décision ; une source désignée vaut même sans issue
            if (!p.sessionId().equals(sessionId) || p.outcome() == InterviewPoint.Outcome.DEFERRED
                    || p.outcome() == null && p.source() == null) {
                continue;
            }
            for (Target t : targets(p, read)) {
                if (seen.add(t.kind() + "|" + t.ref() + "|" + p.id())) {
                    SourceUpdate u = SourceUpdate.open(ids.next(), sessionId, interviewId, p.id(), t.kind(), t.ref(),
                            t.title() != null ? t.title() : title(session, t.kind(), t.ref(), read),
                            fingerprint(session.projectId(), t.kind(), t.ref()).orElse(null), decision(p, read),
                            clock.now());
                    updates.save(u);
                    created.add(u);
                }
            }
        }
        return created;
    }

    /** « À reporter » ouverts de la spécification, avec l'état de leur source. */
    public List<Line> open(UUID sessionId) {
        UUID projectId = sessions.find(sessionId).map(Session::projectId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        return updates.open(sessionId).stream()
                .map(u -> new Line(u, u.sourceChanged(fingerprint(projectId, u.kind(), u.ref()).orElse(null))))
                .toList();
    }

    /** « Marquer comme reporté » : la décision est reportée dans la source, à la main. */
    public void close(UUID sessionId, UUID updateId) {
        SourceUpdate u = updates.find(updateId).filter(x -> x.sessionId().equals(sessionId))
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (u.state() == SourceUpdate.State.OPEN) {
            updates.save(u.closed(clock.now()));
        }
    }

    /** Une source désignée en séance doit être dans la connaissance du projet (FR-028). */
    public void requireKnown(UUID sessionId, InterviewPoint.DesignatedSource source) {
        UUID projectId = sessions.find(sessionId).map(Session::projectId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        SourceRef.Kind kind = kind(source.kind());
        if (kind == null || fingerprint(projectId, kind, source.ref()).isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Cette source n'est pas dans la connaissance du projet : choisissez une page, un ticket ou un élément "
                            + "d'architecture indexé.");
        }
    }

    private Optional<String> fingerprint(UUID projectId, SourceRef.Kind kind, String ref) {
        return knowledge.fingerprint(projectId, kind.knowledgeKinds(), ref);
    }

    /**
     * Titre de la source : celui que la spécification cite, sinon celui de l'élément indexé (la page,
     * ou sa première section, une page Confluence n'étant rangée que par sections), sinon la référence.
     */
    private String title(Session session, SourceRef.Kind kind, String ref, SpecificationContent read) {
        if (read != null) {
            for (SourceDeviation d : read.sourceDeviations()) {
                for (SourceDeviation.Cited c : List.of(d.reference(), d.interpretation())) {
                    if (ref.equals(c.ref()) && c.title() != null && !c.title().isBlank()) {
                        return c.title();
                    }
                }
            }
        }
        Optional<String> section = knowledge.fingerprints(session.projectId()).stream()
                .filter(f -> kind.knowledgeKinds().contains(f.key().kind()) && f.key().ref().startsWith(ref + "#"))
                .findFirst().flatMap(f -> knowledge.find(session.projectId(), f.key())).map(KnowledgeItem::title)
                .filter(t -> !t.isBlank());
        for (var k : kind.knowledgeKinds()) {
            Optional<String> title = knowledge.find(session.projectId(), new KnowledgeIndex.Key(k, ref, 0))
                    .map(KnowledgeItem::title).filter(t -> !t.isBlank());
            if (title.isPresent()) {
                return title.get();
            }
        }
        return section.orElse(ref);
    }

    private record Target(SourceRef.Kind kind, String ref, String title) {
    }

    private static List<Target> targets(InterviewPoint p, SpecificationContent read) {
        List<Target> out = new ArrayList<>();
        if (p.source() != null) {
            SourceRef.Kind kind = kind(p.source().kind());
            if (kind != null) {
                out.add(new Target(kind, p.source().ref(), null));
            }
        }
        if (read == null) {
            return out;
        }
        boolean changes = p.outcome() == InterviewPoint.Outcome.CHANGE || p.outcome() == InterviewPoint.Outcome.REMOVE;
        if (changes && p.anchor() != null) {
            read.useCases().stream().filter(uc -> uc.code().equals(p.anchor().element())).map(UseCase::source)
                    .filter(s -> s != null && SourceUpdate.KINDS.contains(s.kind()) && !s.attachment() && s.ref() != null)
                    .forEach(s -> out.add(new Target(s.kind(), s.ref(), blankToNull(s.excerpt()))));
        }
        if (p.origin() == InterviewPoint.Origin.SOURCE_DEVIATION && p.outcome() != null) {
            read.sourceDeviations().stream().filter(d -> d.code().equals(p.originCode())).findFirst()
                    .ifPresent(d -> out.add(new Target(kind(d.reference().origin()), d.reference().ref(),
                            blankToNull(d.reference().title()))));
        }
        return out;
    }

    /** Texte de la décision, tel que noté ; à défaut, l'énoncé du point tranché. */
    private static String decision(InterviewPoint p, SpecificationContent read) {
        if (!p.text().isBlank() || read == null || p.originCode() == null) {
            return p.text();
        }
        for (Assumption a : read.assumptions()) {
            if (a.code().equals(p.originCode())) {
                return a.statement();
            }
        }
        for (SourceDeviation d : read.sourceDeviations()) {
            if (d.code().equals(p.originCode())) {
                return d.difference();
            }
        }
        for (OpenQuestion q : read.openQuestions()) {
            if (q.code().equals(p.originCode())) {
                return q.question();
            }
        }
        return "";
    }

    private static SourceRef.Kind kind(SourceOrigin origin) {
        if (origin == SourceOrigin.JIRA) {
            return SourceRef.Kind.JIRA_ISSUE;
        }
        return origin == SourceOrigin.ARCHITECTURE ? SourceRef.Kind.ARCH_ELEMENT : SourceRef.Kind.DOC_PAGE;
    }

    private static SourceRef.Kind kind(String raw) {
        try {
            SourceRef.Kind kind = SourceRef.Kind.valueOf(raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT));
            return SourceUpdate.KINDS.contains(kind) ? kind : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
