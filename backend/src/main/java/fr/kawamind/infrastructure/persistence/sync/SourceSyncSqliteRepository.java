package fr.kawamind.infrastructure.persistence.sync;

import fr.kawamind.domain.port.SourceSyncRepository;
import fr.kawamind.domain.sync.SourceChange;
import fr.kawamind.domain.sync.SourceSync;
import fr.kawamind.domain.sync.SourceSyncResult;
import fr.kawamind.domain.sync.SyncInterval;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Table {@code source_sync} (V28, 017) : délai et dernier bilan de chaque source. */
@ApplicationScoped
public class SourceSyncSqliteRepository implements SourceSyncRepository {

    private static final String COLUMNS = """
            select project_id, source_key, interval_minutes, last_run_at, last_result, added, modified, removed,
                   error_code, last_success_at from source_sync""";

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public Optional<SourceSync> find(UUID projectId, String sourceKey) {
        return rows(em.createNativeQuery(COLUMNS + " where project_id = ?1 and source_key = ?2")
                .setParameter(1, projectId.toString()).setParameter(2, sourceKey).getResultList()).stream().findFirst();
    }

    @Override
    @Transactional
    public List<SourceSync> of(UUID projectId) {
        return rows(em.createNativeQuery(COLUMNS + " where project_id = ?1").setParameter(1, projectId.toString())
                .getResultList());
    }

    @Override
    @Transactional
    public List<SourceSync> scheduled() {
        return rows(em.createNativeQuery(COLUMNS + " where interval_minutes is not null").getResultList());
    }

    @Override
    @Transactional
    public void setInterval(UUID projectId, String sourceKey, SyncInterval interval) {
        // un null typé est mal passé aux requêtes natives : la valeur est écrite en littéral contrôlé
        String value = interval.minutes() == null ? "null" : Integer.toString(interval.minutes());
        em.createNativeQuery("insert into source_sync (project_id, source_key, interval_minutes) values (?1, ?2, "
                + value + ") on conflict (project_id, source_key) do update set interval_minutes = excluded.interval_minutes")
                .setParameter(1, projectId.toString()).setParameter(2, sourceKey).executeUpdate();
    }

    @Override
    @Transactional
    public void record(UUID projectId, String sourceKey, SourceSyncResult result) {
        // en échec, la dernière réussite est gardée ; les nulls sont écrits en littéraux (requêtes natives)
        boolean ok = result.succeeded();
        String errorCode = ok ? "null" : "?8";
        String success = ok ? "?3" : "null";
        String kept = ok ? "excluded.last_success_at" : "source_sync.last_success_at";
        jakarta.persistence.Query query = em.createNativeQuery("insert into source_sync (project_id, source_key, "
                + "last_run_at, last_result, added, modified, removed, error_code, last_success_at) "
                + "values (?1, ?2, ?3, ?4, ?5, ?6, ?7, " + errorCode + ", " + success + ") "
                + "on conflict (project_id, source_key) do update set last_run_at = excluded.last_run_at, "
                + "last_result = excluded.last_result, added = excluded.added, modified = excluded.modified, "
                + "removed = excluded.removed, error_code = excluded.error_code, last_success_at = " + kept)
                .setParameter(1, projectId.toString()).setParameter(2, sourceKey)
                .setParameter(3, result.at().toString()).setParameter(4, result.outcome().name())
                .setParameter(5, result.change().added()).setParameter(6, result.change().modified())
                .setParameter(7, result.change().removed());
        if (!ok) {
            query.setParameter(8, result.errorCode());
        }
        query.executeUpdate();
    }

    @SuppressWarnings("unchecked")
    private static List<SourceSync> rows(List<?> rows) {
        return ((List<Object[]>) rows).stream().map(SourceSyncSqliteRepository::row).toList();
    }

    private static SourceSync row(Object[] r) {
        SyncInterval interval = SyncInterval.fromMinutes(r[2] == null ? null : ((Number) r[2]).intValue());
        SourceSyncResult last = null;
        if (r[3] != null && r[4] != null) {
            SourceSyncResult.Outcome outcome = SourceSyncResult.Outcome.valueOf((String) r[4]);
            Instant at = Instant.parse((String) r[3]);
            last = outcome == SourceSyncResult.Outcome.ERROR
                    ? SourceSyncResult.failure(at, (String) r[8])
                    : SourceSyncResult.success(at, new SourceChange(((Number) r[5]).intValue(),
                            ((Number) r[6]).intValue(), ((Number) r[7]).intValue()));
        }
        return new SourceSync(UUID.fromString((String) r[0]), (String) r[1], interval, last,
                r[9] == null ? null : Instant.parse((String) r[9]));
    }
}
