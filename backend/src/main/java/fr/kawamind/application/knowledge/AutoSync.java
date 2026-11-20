package fr.kawamind.application.knowledge;

import fr.kawamind.application.knowledge.SynchronizeKnowledge.Mode;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.SourceSyncRepository;
import fr.kawamind.domain.sync.SourceSync;
import fr.kawamind.domain.sync.SourceSyncResult;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Synchronisation automatique des sources (017, US2, research.md R4). À chaque réveil, les sources
 * échues partent de la plus en retard à la moins en retard, une seule par projet et seulement si le
 * projet n'indexe rien : une échéance attend la fin de l'indexation en cours et n'est jamais mise
 * deux fois en file (FR-010). L'horloge est celle de l'outil ; le réveil vient de l'infrastructure.
 */
public class AutoSync {

    /** Source disparue ou prérequis absent (Jira non configuré, accord refusé, workspace retiré). */
    static final String SOURCE_UNAVAILABLE = "SOURCE_UNAVAILABLE";
    private static final Logger LOG = Logger.getLogger(AutoSync.class);

    private final SourceSyncRepository syncs;
    private final Indexations indexations;
    private final ClockPort clock;
    private final Duration minute;

    public AutoSync(SourceSyncRepository syncs, Indexations indexations, ClockPort clock, Duration minute) {
        this.syncs = syncs;
        this.indexations = indexations;
        this.clock = clock;
        this.minute = minute;
    }

    public void tick() {
        Instant now = clock.now();
        List<SourceSync> due = syncs.scheduled().stream().filter(s -> s.isDue(now, minute))
                .sorted(Comparator.comparing(s -> s.dueAt(minute).orElse(Instant.MAX))).toList();
        Set<UUID> handled = new HashSet<>();
        for (SourceSync s : due) {
            if (handled.contains(s.projectId())) {
                continue;
            }
            try {
                if (launch(s, now)) {
                    handled.add(s.projectId());
                }
            } catch (RuntimeException e) {
                // une source en panne n'arrête pas les autres
                LOG.error("Synchronisation automatique de la source " + s.sourceKey() + " non lancée", e);
                syncs.record(s.projectId(), s.sourceKey(), SourceSyncResult.failure(now, ErrorCode.INTERNAL_ERROR.name()));
            }
        }
    }

    /** Vrai si le projet est désormais occupé (lancée, ou une indexation tournait déjà). */
    private boolean launch(SourceSync s, Instant now) {
        UUID projectId = s.projectId();
        if (indexations.current(projectId).map(i -> i.status() == Indexations.Status.RUNNING).orElse(false)) {
            return true;
        }
        boolean available = indexations.sources(projectId).stream().anyMatch(i -> i.id().equals(s.sourceKey()));
        if (!available) {
            LOG.warnf("Synchronisation automatique : source %s indisponible", s.sourceKey());
            syncs.record(projectId, s.sourceKey(), SourceSyncResult.failure(now, SOURCE_UNAVAILABLE));
            return false;
        }
        try {
            indexations.start(projectId, Mode.INCREMENTAL, List.of(s.sourceKey()));
            LOG.infof("Synchronisation automatique de la source %s", s.sourceKey());
            return true;
        } catch (DomainException e) {
            if (e.code() == ErrorCode.OPERATION_IN_PROGRESS) {
                return true;
            }
            if (e.code() == ErrorCode.PROJECT_NOT_FOUND) {
                return true;
            }
            LOG.warnf("Synchronisation automatique de la source %s refusée (%s)", s.sourceKey(), e.code());
            syncs.record(projectId, s.sourceKey(), SourceSyncResult.failure(now, e.code().name()));
            return false;
        }
    }
}
