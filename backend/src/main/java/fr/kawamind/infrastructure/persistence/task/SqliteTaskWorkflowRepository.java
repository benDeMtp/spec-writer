package fr.kawamind.infrastructure.persistence.task;

import fr.kawamind.domain.port.TaskWorkflowRepository;
import fr.kawamind.domain.task.KnownStatus;
import fr.kawamind.domain.task.StatusName;
import fr.kawamind.domain.task.TaskWorkflow;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Réglage du suivi des tâches (023, V35) et statuts connus, lus dans l'index (research R1, R2). */
@ApplicationScoped
public class SqliteTaskWorkflowRepository implements TaskWorkflowRepository {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> CATEGORIES = List.of("new", "indeterminate", "done");

    @Inject
    EntityManager em;

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public Optional<TaskWorkflow> find(UUID projectId) {
        List<Object[]> rows = em.createNativeQuery("select arrival_status, finished_statuses from project_task_workflow"
                + " where project_id = ?1").setParameter(1, projectId.toString()).getResultList();
        return rows.stream().findFirst().map(r -> new TaskWorkflow((String) r[0],
                JSON.readValue((String) r[1], new TypeReference<List<String>>() { })));
    }

    @Override
    @Transactional
    public void save(UUID projectId, TaskWorkflow workflow, Instant at) {
        em.createNativeQuery("insert into project_task_workflow (project_id, arrival_status, finished_statuses, updated_at)"
                + " values (?1, ?2, ?3, ?4) on conflict(project_id) do update set arrival_status = excluded.arrival_status,"
                + " finished_statuses = excluded.finished_statuses, updated_at = excluded.updated_at")
                .setParameter(1, projectId.toString()).setParameter(2, workflow.arrivalStatus())
                .setParameter(3, JSON.writeValueAsString(workflow.finishedStatuses())).setParameter(4, at.toString())
                .executeUpdate();
    }

    @Override
    @Transactional
    public void delete(UUID projectId) {
        em.createNativeQuery("delete from project_task_workflow where project_id = ?1")
                .setParameter(1, projectId.toString()).executeUpdate();
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<KnownStatus> knownStatuses(UUID projectId) {
        List<Object[]> rows = em.createNativeQuery("select status, status_category, count(*) from knowledge_item"
                + " where project_id = ?1 and kind = 'TICKET' and chunk = 0 and status is not null and status <> ''"
                + " group by status, status_category order by count(*) desc, status")
                .setParameter(1, projectId.toString()).getResultList();
        // le plus fréquent des noms de même forme normalisée l'emporte
        Map<String, KnownStatus> byName = new LinkedHashMap<>();
        for (Object[] r : rows) {
            byName.putIfAbsent(StatusName.normalize((String) r[0]), new KnownStatus((String) r[0], (String) r[1], true));
        }
        List<KnownStatus> out = new ArrayList<>(byName.values());
        out.sort(Comparator.comparingInt((KnownStatus s) -> rank(s.category()))
                .thenComparing(s -> StatusName.normalize(s.name())));
        return out;
    }

    private static int rank(String category) {
        int i = CATEGORIES.indexOf(category);
        return i < 0 ? CATEGORIES.size() : i;
    }
}
