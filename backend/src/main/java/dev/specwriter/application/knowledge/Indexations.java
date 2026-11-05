package dev.specwriter.application.knowledge;

import dev.specwriter.application.knowledge.IndexationProgress.Step;
import dev.specwriter.application.knowledge.SynchronizeKnowledge.Mode;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.ProjectRepository;
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
            Instant startedAt, Instant stepStartedAt, Instant finishedAt, List<IndexationError> errors) {
    }

    private final Map<UUID, Indexation> indexations = new ConcurrentHashMap<>();
    /** Source enregistrée pendant une indexation : relue par une indexation complète juste après. */
    private final Set<UUID> pendingFull = ConcurrentHashMap.newKeySet();
    private final SynchronizeKnowledge synchronize;
    private final ProjectRepository projects;
    private final Executor executor;
    private final ClockPort clock;
    private final boolean automatic;

    public Indexations(SynchronizeKnowledge synchronize, ProjectRepository projects, Executor executor, ClockPort clock) {
        this(synchronize, projects, executor, clock, true);
    }

    /** {@code automatic} faux : les déclenchements automatiques sont ignorés (tests d'intégration). */
    public Indexations(SynchronizeKnowledge synchronize, ProjectRepository projects, Executor executor, ClockPort clock,
            boolean automatic) {
        this.synchronize = synchronize;
        this.projects = projects;
        this.executor = executor;
        this.clock = clock;
        this.automatic = automatic;
    }

    /** Démarre une indexation demandée par l'utilisateur ; refusée si une autre est en cours. */
    public Snapshot start(UUID projectId, Mode mode) {
        if (projects.find(projectId).isEmpty()) {
            throw new DomainException(ErrorCode.PROJECT_NOT_FOUND);
        }
        purge();
        Indexation indexation = new Indexation(projectId, mode, clock.now());
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

        Indexation(UUID projectId, Mode mode, Instant startedAt) {
            this.projectId = projectId;
            this.mode = mode;
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
                synchronize.run(projectId, mode, this);
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

        private synchronized void finish(Status outcome) {
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
        public synchronized void error(IndexationError error) {
            errors.add(error);
            LOG.warnf("Indexation de la connaissance : %s %s (%s%s)", error.kind(),
                    error.location() == null ? "" : error.location(), error.reason(),
                    error.httpStatus() == null ? "" : ", HTTP " + error.httpStatus());
        }

        synchronized Snapshot snapshot() {
            return new Snapshot(projectId, mode, status, step, current, done, total, startedAt, stepStartedAt,
                    finishedAt, List.copyOf(errors));
        }
    }
}
