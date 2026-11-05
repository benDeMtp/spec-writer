package dev.specwriter.application.knowledge;

import dev.specwriter.application.jira.JiraAccess;
import dev.specwriter.application.knowledge.SubtaskReport.Closeness;
import dev.specwriter.application.knowledge.SubtaskReport.Item;
import dev.specwriter.application.knowledge.SubtaskReport.State;
import dev.specwriter.application.knowledge.SubtaskReport.UseCaseRef;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.IssueStatus;
import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.domain.port.IssueTracker;
import dev.specwriter.domain.port.KnowledgeIndex;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.UseCase;
import dev.specwriter.domain.specification.UseCaseChanges;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jboss.logging.Logger;

/**
 * Sous-tâches de la story liée à une spécification, face à une version (FR-046, FR-047,
 * research.md R29). Chaque sous-tâche est rattachée par le sens au cas d'usage le plus proche,
 * quand cette proximité se détache du bruit de fond : le score z de la similarité, mesuré sur les
 * autres tickets du projet, atteint un minimum (les similarités d'un modèle comme e5 sont trop
 * resserrées pour un seuil absolu). Les changements de cas d'usage sont calculés par l'outil
 * ({@link UseCaseChanges}). Les statuts sont relus dans Jira, qui n'est jamais modifié.
 */
public class ReportSubtasks {

    static final int MAX_SUBTASKS = 15;
    static final int MAX_USE_CASE_TEXT = 1_500;
    static final int MAX_SUBTASK_TEXT = 2_000;
    /** Écart, en écarts-types du fond, en dessous duquel un second cas d'usage est cité comme alternative. */
    static final double ALTERNATIVE_MARGIN = 0.5;
    /** Score z à partir duquel une sous-tâche est « très proche » de son cas d'usage. */
    static final double VERY_CLOSE = 3;
    /** Tickets du projet qui mesurent le bruit de fond, et le minimum pour qu'il ait un sens. */
    static final int BACKGROUND = 20;
    static final int MIN_BACKGROUND = 5;
    /** Écart-type plancher : un fond trop uniforme ne doit pas faire exploser les scores. */
    static final double MIN_DEVIATION = 0.005;

    private static final Logger LOG = Logger.getLogger(ReportSubtasks.class);

    private final SessionRepository sessions;
    private final SpecificationRepository versions;
    private final StoryFamily families;
    private final KnowledgeIndex index;
    private final EmbeddingPort embedding;
    private final JiraAccess jira;
    private final IssueTracker tracker;
    private final double minZ;

    public ReportSubtasks(SessionRepository sessions, SpecificationRepository versions, StoryFamily families,
            KnowledgeIndex index, EmbeddingPort embedding, JiraAccess jira, IssueTracker tracker, double minZ) {
        this.sessions = sessions;
        this.versions = versions;
        this.families = families;
        this.index = index;
        this.embedding = embedding;
        this.jira = jira;
        this.tracker = tracker;
        this.minZ = minZ;
    }

    /** La rubrique de la version ; vide si la spécification n'est liée à aucun ticket qui a des sous-tâches ou un parent. */
    public Optional<SubtaskReport> run(UUID sessionId, int number) {
        Session session = sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        SpecificationVersion version = versions.find(sessionId, number)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        StoryFamily.Family family = families.of(session);
        if (family.isEmpty() || family.subtasks().isEmpty() && family.parentKey() == null) {
            return Optional.empty();
        }
        List<UseCase> current = version.content().useCases();
        List<UseCase> previous = versions.list(sessionId).stream().filter(v -> v.number() < number)
                .max(Comparator.comparingInt(SpecificationVersion::number))
                .map(v -> v.content().useCases()).orElse(List.of());
        Map<String, UseCaseChanges.Change> changes = UseCaseChanges.between(previous, current);
        List<UseCase> candidates = candidates(previous, current);

        boolean semantic = embedding.state() == EmbeddingPort.State.READY && !candidates.isEmpty();
        List<float[]> useCaseVectors = List.of();
        List<float[]> background = List.of();
        String problem = null;
        if (embedding.state() != EmbeddingPort.State.READY) {
            problem = Optional.ofNullable(embedding.problem()).orElse("Modèle de recherche indisponible.");
        } else if (semantic) {
            List<KnowledgeItem> others = index.all(session.projectId(), KnowledgeKind.TICKET).stream()
                    .filter(i -> i.chunk() == 0 && !family.contains(i.ref()))
                    .sorted(Comparator.comparing(KnowledgeItem::sourceUpdatedAt,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(BACKGROUND).toList();
            if (others.size() < MIN_BACKGROUND) {
                semantic = false;
                problem = "Trop peu de tickets indexés (" + others.size() + ") pour juger la proximité.";
            } else {
                try {
                    useCaseVectors = embedding.passages(candidates.stream().map(ReportSubtasks::text).toList());
                    background = embedding.passages(others.stream().map(ReportSubtasks::text).toList());
                } catch (RuntimeException e) {
                    LOG.warnf("Sous-tâches : vecteurs indisponibles (%s)", e.getClass().getSimpleName());
                    semantic = false;
                    problem = "Le calcul des vecteurs a échoué.";
                }
            }
        }

        Optional<JiraAccess.Target> target = jira.consented() ? jira.target() : Optional.empty();
        List<Item> items = new ArrayList<>();
        for (String key : family.subtasks().stream().limit(MAX_SUBTASKS).toList()) {
            Optional<KnowledgeItem> indexed = index.find(session.projectId(),
                    new KnowledgeIndex.Key(KnowledgeKind.TICKET, key, 0));
            Optional<Status> status = status(key, indexed, target);
            if (status.isEmpty()) {
                continue; // supprimée dans Jira
            }
            Match match = semantic && indexed.isPresent()
                    ? match(indexed.get(), candidates, useCaseVectors, background) : Match.NONE;
            UseCaseChanges.Change change = match.useCase() == null ? null : changes.get(match.useCase().code());
            State state = match.useCase() == null ? State.UNMATCHED
                    : change == null ? State.CONSISTENT
                    : status.get().closed() ? State.DELIVERED_TO_REWORK : State.TO_REVIEW;
            String title = indexed.map(KnowledgeItem::title).orElse(key);
            items.add(new Item(key, title, target.map(t -> t.issueUrl(key)).orElse(null), status.get().name(),
                    status.get().category(), status.get().fresh(), status.get().date(), match.useCase(),
                    match.alternative(), match.closeness(), change, state));
        }
        items.sort(Comparator.comparingInt((Item i) -> i.state().ordinal()).thenComparing(Item::key));
        return Optional.of(new SubtaskReport(family.storyKey(), family.parentKey(),
                semantic ? SubtaskReport.Matching.SEMANTIC : SubtaskReport.Matching.UNAVAILABLE,
                semantic ? null : problem, items));
    }

    /**
     * Cas d'usage auxquels une sous-tâche peut se rattacher : ceux de la version précédente, contre
     * lesquels les sous-tâches ont été écrites, puis les nouveaux de la version.
     */
    private static List<UseCase> candidates(List<UseCase> previous, List<UseCase> current) {
        Map<String, UseCase> byCode = new LinkedHashMap<>();
        previous.forEach(uc -> byCode.put(uc.code(), uc));
        current.forEach(uc -> byCode.putIfAbsent(uc.code(), uc));
        return List.copyOf(byCode.values());
    }

    private record Match(UseCaseRef useCase, UseCaseRef alternative, Closeness closeness) {
        static final Match NONE = new Match(null, null, null);
    }

    private Match match(KnowledgeItem subtask, List<UseCase> candidates, List<float[]> vectors,
            List<float[]> background) {
        if (vectors.size() != candidates.size() || background.isEmpty()) {
            return Match.NONE;
        }
        float[] own;
        try {
            List<float[]> computed = embedding.passages(List.of(text(subtask)));
            if (computed.isEmpty()) {
                return Match.NONE;
            }
            own = computed.getFirst();
        } catch (RuntimeException e) {
            LOG.warnf("Sous-tâches : vecteur de %s indisponible (%s)", subtask.ref(), e.getClass().getSimpleName());
            return Match.NONE;
        }
        // bruit de fond : proximité de la sous-tâche avec les autres tickets du projet
        double mean = 0;
        for (float[] b : background) {
            mean += cosine(own, b);
        }
        mean /= background.size();
        double variance = 0;
        for (float[] b : background) {
            variance += Math.pow(cosine(own, b) - mean, 2);
        }
        double deviation = Math.max(Math.sqrt(variance / background.size()), MIN_DEVIATION);

        int best = -1;
        int second = -1;
        double[] z = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            z[i] = (cosine(own, vectors.get(i)) - mean) / deviation;
            if (best < 0 || z[i] > z[best]) {
                second = best;
                best = i;
            } else if (second < 0 || z[i] > z[second]) {
                second = i;
            }
        }
        if (LOG.isDebugEnabled()) {
            // clés, codes et scores seulement : aucun contenu (calibrage, research.md R29)
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < candidates.size(); i++) {
                out.append(' ').append(candidates.get(i).code()).append(" z=").append(String.format(Locale.ROOT, "%.2f", z[i]));
            }
            LOG.debugf("Sous-tâches : %s ->%s (fond %.3f ± %.3f, minimum %s)", subtask.ref(), out, mean, deviation, minZ);
        }
        if (best < 0 || z[best] < minZ) {
            return Match.NONE;
        }
        UseCaseRef alternative = second >= 0 && z[second] >= minZ && z[best] - z[second] < ALTERNATIVE_MARGIN
                ? ref(candidates.get(second)) : null;
        return new Match(ref(candidates.get(best)), alternative,
                z[best] >= VERY_CLOSE ? Closeness.VERY_CLOSE : Closeness.CLOSE);
    }

    private static UseCaseRef ref(UseCase uc) {
        return new UseCaseRef(uc.code(), uc.title());
    }

    private record Status(String name, String category, boolean closed, boolean fresh, Instant date) {
    }

    /**
     * Statut relu dans Jira, sinon indexé ; vide si la sous-tâche a été supprimée. Une sous-tâche
     * indexée fermée est montrée depuis l'index, comme à jour, sans requête (FR-006 de 003).
     */
    private Optional<Status> status(String key, Optional<KnowledgeItem> indexed, Optional<JiraAccess.Target> target) {
        if (indexed.filter(KnowledgeItem::closed).isPresent()) {
            KnowledgeItem i = indexed.get();
            return Optional.of(new Status(i.status(), i.statusCategory(), true, true, i.sourceUpdatedAt()));
        }
        if (target.isPresent()) {
            try {
                IssueStatus s = tracker.issueStatus(target.get().endpoint(), key);
                return Optional.of(new Status(s.status(), s.statusCategory(), s.closed(), true, s.updated()));
            } catch (IssueTrackerException e) {
                if (e.kind() == IssueTrackerException.Kind.NOT_FOUND) {
                    return Optional.empty();
                }
                LOG.infof("Sous-tâches : statut de %s non relu (%s), statut indexé montré", key, e.kind());
            }
        }
        return Optional.of(indexed.map(i -> new Status(i.status(), i.statusCategory(), i.closed(), false,
                i.sourceUpdatedAt())).orElse(new Status(null, null, false, false, null)));
    }

    /** Code, titre, objectif et scénario nominal, 1 500 caractères au plus. */
    static String text(UseCase uc) {
        String text = Stream.concat(Stream.of(uc.code() + " " + uc.title(), uc.goal()), uc.nominalFlow().stream())
                .filter(s -> s != null && !s.isBlank()).collect(Collectors.joining("\n"));
        return text.length() <= MAX_USE_CASE_TEXT ? text : text.substring(0, MAX_USE_CASE_TEXT);
    }

    private static String text(KnowledgeItem ticket) {
        String text = ticket.title() + "\n" + ticket.body();
        return text.length() <= MAX_SUBTASK_TEXT ? text : text.substring(0, MAX_SUBTASK_TEXT);
    }

    static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return -1;
        }
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return na == 0 || nb == 0 ? -1 : dot / Math.sqrt(na * nb);
    }
}
