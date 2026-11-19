package fr.kawamind.application.knowledge;

import fr.kawamind.application.knowledge.IndexationProgress.Step;
import fr.kawamind.application.knowledge.SynchronizeKnowledge.Mode;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.port.SourceSyncRepository;
import fr.kawamind.domain.sync.SourceChange;
import fr.kawamind.domain.sync.SourceSyncResult;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.jboss.logging.Logger;

/**
 * Indexations en arrière-plan, une par projet au plus (research.md R14, R20). L'état est suivi par
 * interrogation et reste consultable 30 minutes après la fin. L'interruption est immédiate ; ce qui
 * est indexé reste. L'avancement n'est jamais journalisé ; les erreurs le sont, sans contenu.
 */
public class Indexations {

    public static final Duration KEEP = Duration.ofMinutes(30);
    private static final Logger LOG = Logger.getLogger(Indexations.class);

    public enum Status { RUNNING, SUCCEEDED, FAILED, CANCELLED }

    /** État d'une indexation à un instant donné. */
    public record Snapshot(UUID projectId, Mode mode, Status status, Step step, String current, int done, int total,
            Instant startedAt, Instant stepStartedAt, Instant finishedAt, List<IndexationError> errors,
            TicketSummary tickets, List<String> sources) {
    }

    private final Map<UUID, Indexation> indexations = new ConcurrentHashMap<>();
    /** Source enregistrée pendant une indexation : relue par une indexation complète juste après. */
    private final Set<UUID> pendingFull = ConcurrentHashMap.newKeySet();
    private final SynchronizeKnowledge synchronize;
    private final ProjectRepository projects;
    private final Executor executor;
    private final ClockPort clock;
    private final boolean automatic;
    /** Bilans des sources (017) ; absent, ils ne sont pas gardés. */
    private final SourceSyncRepository syncs;

    public Indexations(SynchronizeKnowledge synchronize, ProjectRepository projects, Executor executor, ClockPort clock) {
        this(synchronize, projects, executor, clock, true);
    }

    /** {@code automatic} faux : les déclenchements automatiques sont ignorés (tests d'intégration). */
    public Indexations(SynchronizeKnowledge synchronize, ProjectRepository projects, Executor executor, ClockPort clock,
            boolean automatic) {
        this(synchronize, projects, executor, clock, automatic, null);
    }

    public Indexations(SynchronizeKnowledge synchronize, ProjectRepository projects, Executor executor, ClockPort clock,
            boolean automatic, SourceSyncRepository syncs) {
        this.synchronize = synchronize;
        this.projects = projects;
        this.executor = executor;
        this.clock = clock;
        this.automatic = automatic;
        this.syncs = syncs;
    }

    /** Démarre une indexation demandée par l'utilisateur ; refusée si une autre est en cours. */
    public Snapshot start(UUID projectId, Mode mode) {
        return start(projectId, mode, null);
    }

    /**
     * Indexation des seules sources choisies ; {@code null} pour toutes. En mode complet, c'est la
     * réindexation par source de la 005 ; en incrémental, la synchronisation d'une source (017, FR-009).
     */
    public Snapshot start(UUID projectId, Mode mode, List<String> sources) {
        if (projects.find(projectId).isEmpty()) {
            throw new DomainException(ErrorCode.PROJECT_NOT_FOUND);
        }
        IndexationScope scope = sources == null ? IndexationScope.ALL : synchronize.scope(projectId, sources);
        purge();
        Indexation indexation = new Indexation(projectId, mode, scope, sources == null ? null : List.copyOf(sources),
                clock.now());
        Indexation existing = indexations.compute(projectId, (id, e) -> e != null && e.running() ? e : indexation);
        if (existing != indexation) {
            throw new DomainException(ErrorCode.OPERATION_IN_PROGRESS,
                    "Une indexation de la connaissance est déjà en cours pour ce projet.");
        }
        executor.execute(indexation::run);
        return indexation.snapshot();
    }

    /**
     * Déclenchement automatique (enregistrement d'une source, ouverture d'un besoin) : sans attendre.
     * Pendant une indexation, l'ouverture d'un besoin est sans effet ; une source enregistrée est
     * relue par une indexation complète dès la fin de celle en cours.
     */
    public void trigger(UUID projectId, Mode mode) {
        if (!automatic) {
            return;
        }
        try {
            start(projectId, mode);
        } catch (DomainException e) {
            if (e.code() == ErrorCode.OPERATION_IN_PROGRESS && mode == Mode.FULL) {
                pendingFull.add(projectId);
            }
            LOG.debugf("Indexation non déclenchée (%s)", e.code());
        }
    }

    /** Fin d'une indexation : lance l'indexation complète demandée entre-temps. */
    private void followUp(UUID projectId) {
        if (pendingFull.remove(projectId)) {
            trigger(projectId, Mode.FULL);
        }
    }

    /** Sources proposées au choix d'une réindexation (005, research.md R5). */
    public List<IndexableSource> sources(UUID projectId) {
        return synchronize.sources(projectId);
    }

    public Optional<Snapshot> current(UUID projectId) {
        purge();
        return Optional.ofNullable(indexations.get(projectId)).map(Indexation::snapshot);
    }

    public void cancel(UUID projectId) {
        pendingFull.remove(projectId);
        Indexation i = indexations.get(projectId);
        if (i != null) {
            i.cancel();
        }
    }

    private void purge() {
        Instant limit = clock.now().minus(KEEP);
        indexations.values().removeIf(i -> i.finishedBefore(limit));
    }

    private final class Indexation implements IndexationProgress {
        private final UUID projectId;
        private final Mode mode;
        private final IndexationScope scope;
        private final List<String> sources;
        private final Instant startedAt;
        private final List<IndexationError> errors = new ArrayList<>();
        private volatile boolean cancelRequested;
        private Thread worker;
        private Status status = Status.RUNNING;
        private Step step;
        private Instant stepStartedAt;
        private String current;
        private int done;
        private int total;
        private Instant finishedAt;
        private TicketSummary tickets;
        /** Sources dont le bilan est déjà enregistré. */
        private final Set<String> reported = ConcurrentHashMap.newKeySet();

        Indexation(UUID projectId, Mode mode, IndexationScope scope, List<String> sources, Instant startedAt) {
            this.projectId = projectId;
            this.mode = mode;
            this.scope = scope;
            this.sources = sources;
            this.startedAt = startedAt;
        }

        void run() {
            synchronized (this) {
                if (cancelRequested) {
                    return;
                }
                worker = Thread.currentThread();
            }
            try {
                synchronize.run(projectId, mode, scope, this);
                finish(Status.SUCCEEDED);
            } catch (IndexationProgress.Cancelled e) {
                finish(Status.CANCELLED);
            } catch (RuntimeException e) {
                if (cancelRequested) {
                    LOG.debugf("Indexation interrompue pendant un appel (%s)", e.getClass().getSimpleName());
                } else if (e instanceof DomainException d) {
                    LOG.warnf("Indexation de la connaissance en échec à l'étape %s : %s", stepName(), d.code());
                    error(new IndexationError(IndexationError.Kind.FAILURE, null, d.code().name(), null, d.getMessage()));
                    finish(Status.FAILED);
                } else {
                    LOG.error("Indexation de la connaissance en échec : erreur inattendue", e);
                    error(new IndexationError(IndexationError.Kind.FAILURE, null, ErrorCode.INTERNAL_ERROR.name(), null,
                            ErrorCode.INTERNAL_ERROR.defaultMessage()));
                    finish(Status.FAILED);
                }
            } catch (LinkageError e) {
                // bibliothèque absente ou native introuvable : l'indexation ne doit pas rester « en cours »
                LOG.error("Indexation de la connaissance en échec : bibliothèque manquante", e);
                error(new IndexationError(IndexationError.Kind.FAILURE, null, ErrorCode.INTERNAL_ERROR.name(), null,
                        ErrorCode.INTERNAL_ERROR.defaultMessage()));
                finish(Status.FAILED);
            } finally {
                synchronized (this) {
                    worker = null;
                }
                // l'interruption visait l'appel coupé, pas la suite du thread
                Thread.interrupted();
            }
            followUp(projectId);
        }

        private synchronized String stepName() {
            return step == null ? "PREPARING" : step.name();
        }

        private void finish(Status outcome) {
            boolean first;
            synchronized (this) {
                first = status == Status.RUNNING;
                end(outcome);
            }
            // indexation interrompue ou en échec : les sources demandées sans bilan sont en échec, pour
            // qu'une échéance ne reparte pas aussitôt (017, FR-012)
            if (first && outcome != Status.SUCCEEDED && sources != null) {
                sources.stream().filter(k -> !reported.contains(k)).forEach(k -> sourceFailed(k, outcome.name()));
            }
        }

        private synchronized void end(Status outcome) {
            if (status != Status.RUNNING) {
                return;
            }
            status = outcome;
            current = null;
            finishedAt = clock.now();
        }

        synchronized boolean running() {
            return status == Status.RUNNING;
        }

        synchronized boolean finishedBefore(Instant limit) {
            return finishedAt != null && finishedAt.isBefore(limit);
        }

        synchronized void cancel() {
            cancelRequested = true;
            if (worker != null) {
                worker.interrupt();
            }
            finish(Status.CANCELLED);
        }

        @Override
        public boolean cancelled() {
            return cancelRequested;
        }

        @Override
        public synchronized void step(Step newStep, int newTotal) {
            step = newStep;
            stepStartedAt = clock.now();
            current = null;
            done = 0;
            total = newTotal;
        }

        @Override
        public synchronized void advance(String newCurrent, int newDone, int newTotal) {
            current = newCurrent;
            done = Math.max(done, newDone);
            total = Math.max(total, newTotal);
        }

        @Override
        public void sourceDone(String sourceKey, SourceChange change) {
            record(sourceKey, SourceSyncResult.success(clock.now(), change));
        }

        @Override
        public void sourceFailed(String sourceKey, String errorCode) {
            record(sourceKey, SourceSyncResult.failure(clock.now(), errorCode));
        }

        private void record(String sourceKey, SourceSyncResult result) {
            reported.add(sourceKey);
            if (syncs == null) {
                return;
            }
            try {
                syncs.record(projectId, sourceKey, result);
            } catch (RuntimeException e) {
                LOG.error("Bilan de la source " + sourceKey + " non enregistré", e);
            }
        }

        @Override
        public synchronized void tickets(TicketSummary summary) {
            tickets = summary;
        }

        @Override
        public synchronized void error(IndexationError error) {
            errors.add(error);
            LOG.warnf("Indexation de la connaissance : %s %s (%s%s)", error.kind(),
                    error.location() == null ? "" : error.location(), error.reason(),
                    error.httpStatus() == null ? "" : ", HTTP " + error.httpStatus());
        }

        synchronized Snapshot snapshot() {
            return new Snapshot(projectId, mode, status, step, current, done, total, startedAt, stepStartedAt,
                    finishedAt, List.copyOf(errors), tickets, sources);
        }
    }
}
