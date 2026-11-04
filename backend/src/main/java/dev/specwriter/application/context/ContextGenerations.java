package dev.specwriter.application.context;

import dev.specwriter.application.context.ContextProposalStore.ContextProposal;
import dev.specwriter.application.context.GenerationProgress.Step;
import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentService;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.jboss.logging.Logger;

/**
 * Générations de contexte en arrière-plan, une par projet au plus (FR-013f, FR-013g, research.md
 * R14). L'état d'une génération est suivi par interrogation et reste consultable 30 minutes après
 * sa fin. L'avancement n'est jamais journalisé ; les erreurs le sont, sans contenu.
 */
public class ContextGenerations {

    public static final Duration KEEP = Duration.ofMinutes(30);
    private static final Logger LOG = Logger.getLogger(ContextGenerations.class);

    public enum Status { RUNNING, SUCCEEDED, FAILED, CANCELLED }

    /** État d'une génération à un instant donné. */
    public record Snapshot(UUID projectId, Status status, Step step, String current, int done, int total,
            Instant startedAt, Instant finishedAt, List<GenerationError> errors, ContextProposal proposal) {
    }

    private final Map<UUID, Generation> generations = new ConcurrentHashMap<>();
    private final ProposeProjectContext propose;
    private final ProjectRepository projects;
    private final ConsentPolicy consent;
    private final ContextProposalStore proposals;
    private final Executor executor;
    private final ClockPort clock;

    public ContextGenerations(ProposeProjectContext propose, ProjectRepository projects, ConsentPolicy consent,
            ContextProposalStore proposals, Executor executor, ClockPort clock) {
        this.propose = propose;
        this.projects = projects;
        this.consent = consent;
        this.proposals = proposals;
        this.executor = executor;
        this.clock = clock;
    }

    /** Démarre une génération ; le consentement est vérifié avant, pour être demandé à l'écran. */
    public Snapshot start(UUID projectId) {
        if (projects.find(projectId).isEmpty()) {
            throw new DomainException(ErrorCode.PROJECT_NOT_FOUND);
        }
        consent.requireGranted(ConsentService.COPILOT);
        purge();
        return launch(new Generation(projectId, clock.now(), null));
    }

    /**
     * Révise une proposition avec les réponses données, à partir des faits gardés par sa génération,
     * sans relire les sources (FR-013h, research.md R16). La proposition révisée remplace l'ancienne.
     */
    public Snapshot revise(UUID projectId, UUID proposalId) {
        consent.requireGranted(ConsentService.COPILOT);
        purge();
        Generation done = generations.get(projectId);
        ProposeProjectContext.Basis basis = done == null ? null : done.basisFor(proposalId);
        if (basis == null || proposals.find(projectId, proposalId).isEmpty()) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Cette proposition a expiré. Régénérez le contexte.");
        }
        Snapshot started = launch(new Generation(projectId, clock.now(), basis));
        proposals.discard(projectId, proposalId);
        return started;
    }

    private Snapshot launch(Generation generation) {
        Generation previous = generations.compute(generation.projectId,
                (id, existing) -> existing != null && existing.running() ? existing : generation);
        if (previous != generation) {
            throw new DomainException(ErrorCode.OPERATION_IN_PROGRESS,
                    "Une génération du contexte est déjà en cours pour ce projet.");
        }
        executor.execute(generation::run);
        return generation.snapshot();
    }

    public Optional<Snapshot> current(UUID projectId) {
        purge();
        return Optional.ofNullable(generations.get(projectId)).map(Generation::snapshot);
    }

    /** Demande l'interruption ; elle est constatée avant la page, le ticket ou l'appel suivant. */
    public void cancel(UUID projectId) {
        Generation g = generations.get(projectId);
        if (g != null) {
            g.cancel();
        }
    }

    private void purge() {
        Instant limit = clock.now().minus(KEEP);
        generations.values().removeIf(g -> g.finishedBefore(limit));
    }

    private final class Generation implements GenerationProgress {
        private final UUID projectId;
        private final Instant startedAt;
        private final List<GenerationError> errors = new ArrayList<>();
        private volatile boolean cancelRequested;
        private Status status = Status.RUNNING;
        private Step step;
        private String current;
        private int done;
        private int total;
        private Instant finishedAt;
        private ContextProposal proposal;
        /** Faits extraits, gardés pour réviser la proposition ; jamais renvoyés par l'API. */
        private ProposeProjectContext.Basis basis;

        Generation(UUID projectId, Instant startedAt, ProposeProjectContext.Basis basis) {
            this.projectId = projectId;
            this.startedAt = startedAt;
            this.basis = basis;
        }

        synchronized ProposeProjectContext.Basis basisFor(UUID proposalId) {
            return status == Status.SUCCEEDED && proposal != null && proposal.id().equals(proposalId) ? basis : null;
        }

        void run() {
            try {
                ProposeProjectContext.Basis base;
                synchronized (this) {
                    base = basis;
                }
                if (base == null) {
                    base = propose.prepare(projectId, this);
                    synchronized (this) {
                        basis = base;
                    }
                }
                ContextProposal result = propose.draft(projectId, base, this);
                if (cancelRequested) {
                    proposals.discard(projectId, result.id());
                    finish(Status.CANCELLED, null);
                } else {
                    finish(Status.SUCCEEDED, result);
                }
            } catch (GenerationProgress.Cancelled e) {
                finish(Status.CANCELLED, null);
            } catch (DomainException e) {
                LOG.warnf("Génération du contexte en échec : %s", e.code());
                error(new GenerationError(GenerationError.Kind.FAILURE, null, e.code().name(), null, e.getMessage()));
                finish(Status.FAILED, null);
            } catch (RuntimeException e) {
                LOG.error("Génération du contexte en échec : erreur inattendue", e);
                error(new GenerationError(GenerationError.Kind.FAILURE, null, ErrorCode.INTERNAL_ERROR.name(), null,
                        ErrorCode.INTERNAL_ERROR.defaultMessage()));
                finish(Status.FAILED, null);
            }
        }

        private synchronized void finish(Status outcome, ContextProposal result) {
            status = outcome;
            proposal = result;
            current = null;
            finishedAt = clock.now();
        }

        synchronized boolean running() {
            return status == Status.RUNNING;
        }

        synchronized boolean finishedBefore(Instant limit) {
            return finishedAt != null && finishedAt.isBefore(limit);
        }

        void cancel() {
            cancelRequested = true;
        }

        @Override
        public boolean cancelled() {
            return cancelRequested;
        }

        @Override
        public synchronized void step(Step newStep, int newTotal) {
            step = newStep;
            current = null;
            done = 0;
            total = newTotal;
        }

        @Override
        public synchronized void advance(String newCurrent, int newDone, int newTotal) {
            current = newCurrent;
            // lecture en parallèle : l'avancement ne recule jamais
            done = Math.max(done, newDone);
            total = Math.max(total, newTotal);
        }

        @Override
        public synchronized void error(GenerationError error) {
            errors.add(error);
            if (error.kind() == GenerationError.Kind.JIRA || error.kind() == GenerationError.Kind.CORRECTION) {
                LOG.warnf("Génération du contexte : %s %s (%s)", error.kind(), error.location() == null ? "" : error.location(),
                        error.reason());
            }
        }

        synchronized Snapshot snapshot() {
            return new Snapshot(projectId, status, step, current, done, total, startedAt, finishedAt, List.copyOf(errors),
                    proposal);
        }
    }
}
