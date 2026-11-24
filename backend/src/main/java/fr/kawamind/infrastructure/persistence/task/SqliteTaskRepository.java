package fr.kawamind.infrastructure.persistence.task;

import fr.kawamind.domain.port.TaskRepository;
import fr.kawamind.domain.task.Basis;
import fr.kawamind.domain.task.BasisKind;
import fr.kawamind.domain.task.Holder;
import fr.kawamind.domain.task.ProposedTask;
import fr.kawamind.domain.task.SpecTask;
import fr.kawamind.domain.task.TaskCreation;
import fr.kawamind.domain.task.TaskOrigin;
import fr.kawamind.domain.task.TaskProposal;
import fr.kawamind.domain.task.TaskStatus;
import fr.kawamind.domain.task.TaskTracking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Tâches d'une spécification en base (015, V26), en SQL natif comme les autres dépôts SQLite. */
@ApplicationScoped
public class SqliteTaskRepository implements TaskRepository {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String COLUMNS = "id, session_id, position, title, outcome, basis_kind, basis_ref, origin,"
            + " creation, jira_key, status, jira_status_name, holder_connection, holder_agent, held_since,"
            + " pending_target, pending_comment, completion_report, last_read_at";

    @Inject
    EntityManager em;

    @Transactional
    @Override
    @SuppressWarnings("unchecked")
    public List<SpecTask> tasks(UUID sessionId) {
        String session = sessionId.toString();
        Map<String, List<UUID>> deps = new LinkedHashMap<>();
        List<Object[]> links = em.createNativeQuery("select d.task_id, d.depends_on_id from spec_task_dependency d"
                + " join spec_task t on t.id = d.task_id where t.session_id = ?1 order by d.depends_on_id")
                .setParameter(1, session).getResultList();
        for (Object[] l : links) {
            deps.computeIfAbsent((String) l[0], k -> new ArrayList<>()).add(UUID.fromString((String) l[1]));
        }
        List<Object[]> rows = em.createNativeQuery("select " + COLUMNS + " from spec_task where session_id = ?1"
                + " order by position").setParameter(1, session).getResultList();
        return rows.stream().map(r -> task(r, deps.getOrDefault((String) r[0], List.of()))).toList();
    }

    private static SpecTask task(Object[] r, List<UUID> deps) {
        BasisKind kind = BasisKind.valueOf((String) r[5]);
        Holder holder = r[12] == null ? null
                : new Holder((String) r[12], (String) r[13], Instant.parse((String) r[14]));
        return new SpecTask(UUID.fromString((String) r[0]), UUID.fromString((String) r[1]),
                ((Number) r[2]).intValue(), (String) r[3], (String) r[4], new Basis(kind, (String) r[6]),
                TaskOrigin.valueOf((String) r[7]), TaskCreation.valueOf((String) r[8]), (String) r[9],
                TaskStatus.valueOf((String) r[10]), (String) r[11], holder,
                r[15] == null ? null : TaskStatus.valueOf((String) r[15]), ((Number) r[16]).intValue() == 1,
                (String) r[17], r[18] == null ? null : Instant.parse((String) r[18]), deps);
    }

    @Override
    @Transactional
    public void replaceAll(UUID sessionId, List<SpecTask> tasks) {
        em.createNativeQuery("delete from spec_task where session_id = ?1").setParameter(1, sessionId.toString())
                .executeUpdate();
        for (SpecTask t : tasks) {
            if (!t.sessionId().equals(sessionId)) {
                throw new IllegalArgumentException("tâche d'une autre session");
            }
            var insert = em.createNativeQuery("insert into spec_task (" + COLUMNS + ") values (?1, ?2, ?3, ?4, ?5, ?6,"
                    + " ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16, ?17, ?18, ?19)");
            bind(insert, t);
            insert.executeUpdate();
        }
        for (SpecTask t : tasks) {
            for (UUID d : t.dependsOn()) {
                em.createNativeQuery("insert into spec_task_dependency (task_id, depends_on_id) values (?1, ?2)")
                        .setParameter(1, t.id().toString()).setParameter(2, d.toString()).executeUpdate();
            }
        }
    }

    @Override
    @Transactional
    public void update(SpecTask t) {
        var update = em.createNativeQuery("update spec_task set id = ?1, session_id = ?2, position = ?3, title = ?4,"
                + " outcome = ?5, basis_kind = ?6, basis_ref = ?7, origin = ?8, creation = ?9, jira_key = ?10,"
                + " status = ?11, jira_status_name = ?12, holder_connection = ?13, holder_agent = ?14,"
                + " held_since = ?15, pending_target = ?16, pending_comment = ?17, completion_report = ?18,"
                + " last_read_at = ?19 where id = ?1");
        bind(update, t);
        update.executeUpdate();
    }

    private static void bind(jakarta.persistence.Query q, SpecTask t) {
        Holder h = t.holder();
        q.setParameter(1, t.id().toString()).setParameter(2, t.sessionId().toString()).setParameter(3, t.position())
                .setParameter(4, t.title()).setParameter(5, t.outcome()).setParameter(6, t.basis().kind().name())
                .setParameter(7, t.basis().ref()).setParameter(8, t.origin().name())
                .setParameter(9, t.creation().name()).setParameter(10, t.jiraKey()).setParameter(11, t.status().name())
                .setParameter(12, t.jiraStatusName()).setParameter(13, h == null ? null : h.connection())
                .setParameter(14, h == null ? null : h.agentName())
                .setParameter(15, h == null ? null : h.since().toString())
                .setParameter(16, t.pendingTarget() == null ? null : t.pendingTarget().name())
                .setParameter(17, t.pendingComment() ? 1 : 0).setParameter(18, t.report())
                .setParameter(19, t.lastReadAt() == null ? null : t.lastReadAt().toString());
    }

    @Transactional
    @Override
    @SuppressWarnings("unchecked")
    public Optional<TaskProposal> proposal(UUID sessionId) {
        List<String> rows = em.createNativeQuery("select content from spec_task_proposal where session_id = ?1")
                .setParameter(1, sessionId.toString()).getResultList();
        return rows.stream().findFirst().map(SqliteTaskRepository::fromJson);
    }

    @Override
    @Transactional
    public void saveProposal(UUID sessionId, TaskProposal proposal, Instant at) {
        deleteProposal(sessionId);
        em.createNativeQuery("insert into spec_task_proposal (session_id, content, created_at) values (?1, ?2, ?3)")
                .setParameter(1, sessionId.toString()).setParameter(2, toJson(proposal)).setParameter(3, at.toString())
                .executeUpdate();
    }

    @Override
    @Transactional
    public void deleteProposal(UUID sessionId) {
        em.createNativeQuery("delete from spec_task_proposal where session_id = ?1")
                .setParameter(1, sessionId.toString()).executeUpdate();
    }

    @Transactional
    @Override
    @SuppressWarnings("unchecked")
    public Map<UUID, String> descriptions(UUID sessionId) {
        Map<UUID, String> out = new LinkedHashMap<>();
        List<Object[]> rows = em.createNativeQuery("select id, jira_description from spec_task where session_id = ?1"
                + " and jira_description is not null").setParameter(1, sessionId.toString()).getResultList();
        rows.forEach(r -> out.put(UUID.fromString((String) r[0]), (String) r[1]));
        return out;
    }

    @Transactional
    @Override
    public void describe(UUID taskId, String description) {
        if (description == null) {
            // un null lié en paramètre natif n'a pas de type pour Hibernate
            em.createNativeQuery("update spec_task set jira_description = null where id = ?1")
                    .setParameter(1, taskId.toString()).executeUpdate();
            return;
        }
        em.createNativeQuery("update spec_task set jira_description = ?1 where id = ?2").setParameter(1, description)
                .setParameter(2, taskId.toString()).executeUpdate();
    }

    @Transactional
    @Override
    public TaskTracking tracking(UUID sessionId) {
        @SuppressWarnings("unchecked")
        List<String> rows = em.createNativeQuery("select task_tracking from session where id = ?1")
                .setParameter(1, sessionId.toString()).getResultList();
        return rows.isEmpty() ? TaskTracking.ACTIVE : TaskTracking.valueOf(rows.get(0));
    }

    @Override
    @Transactional
    public void setTracking(UUID sessionId, TaskTracking tracking) {
        em.createNativeQuery("update session set task_tracking = ?1 where id = ?2").setParameter(1, tracking.name())
                .setParameter(2, sessionId.toString()).executeUpdate();
    }

    static String toJson(TaskProposal p) {
        ObjectNode root = JSON.createObjectNode();
        root.put("basing", p.basing().name());
        ArrayNode tasks = root.putArray("tasks");
        for (ProposedTask t : p.tasks()) {
            ObjectNode n = tasks.addObject();
            n.put("title", t.title());
            n.put("outcome", t.outcome());
            n.put("basisKind", t.basis().kind().name());
            n.put("basisRef", t.basis().ref());
            ArrayNode deps = n.putArray("dependsOn");
            t.dependsOn().forEach(deps::add);
            n.put("manual", t.manual());
        }
        return JSON.writeValueAsString(root);
    }

    static TaskProposal fromJson(String content) {
        JsonNode root = JSON.readTree(content);
        List<ProposedTask> tasks = new ArrayList<>();
        for (JsonNode n : root.path("tasks")) {
            List<Integer> deps = new ArrayList<>();
            n.path("dependsOn").forEach(d -> deps.add(d.asInt()));
            JsonNode ref = n.path("basisRef");
            tasks.add(new ProposedTask(n.path("title").asString(), n.path("outcome").asString(),
                    new Basis(BasisKind.valueOf(n.path("basisKind").asString()), ref.isNull() || ref.isMissingNode()
                            ? null : ref.asString()), deps, n.path("manual").asBoolean()));
        }
        return new TaskProposal(TaskProposal.Basing.valueOf(root.path("basing").asString()), tasks);
    }
}
