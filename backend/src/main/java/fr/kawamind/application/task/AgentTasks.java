package fr.kawamind.application.task;

import fr.kawamind.application.agent.AgentJournal;
import fr.kawamind.domain.agent.AgentOperation;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.AgentConnections;
import fr.kawamind.domain.session.ImplementationStatus;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.task.Holder;
import fr.kawamind.domain.task.SpecTask;
import fr.kawamind.domain.task.StatusCategory;
import fr.kawamind.domain.task.TaskProgress;
import fr.kawamind.domain.task.TaskStatus;
import fr.kawamind.domain.task.TaskTracking;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Outils de tâches de la passerelle MCP (015, US2, contracts/mcp-tools.md) : prochaine tâche, prise
 * et fin d'une tâche par un agent. Le choix est déterministe ({@link TaskProgress#next}) ; une tâche
 * est réservée à la connexion qui l'a prise (R4). Jira d'abord ; injoignable, la transition attend
 * (« à synchroniser ») et l'agent en est prévenu (R5). Chaque appel est journalisé, sans contenu.
 */
public class AgentTasks {

    static final String PENDING_MESSAGE = "Jira est injoignable : la transition est enregistrée dans spec-writer et "
            + "sera envoyée à Jira dès que possible. Vous pouvez continuer ou attendre.";

    /**
     * Une tâche telle qu'un agent la voit. {@code dependsOn} : clés Jira des préalables ;
     * {@code description} : celle de la sous-tâche dans Jira, en Markdown, nulle si vide.
     */
    public record TaskInfo(String key, String title, String outcome, String description, int position,
            List<String> dependsOn,
            TaskStatus status, String heldBy, boolean heldByYou, String jiraSync, String report) {
    }

    /** Tâches d'une feature, pour {@code get_feature}. */
    public record FeatureTasks(List<TaskInfo> tasks, int done, int total, TaskTracking tracking, boolean readProblem) {
    }

    /** Réponse de {@code next_task} : {@code TASK}, {@code NONE_LEFT} ou {@code ALL_HELD}. */
    public record NextAnswer(String outcome, TaskInfo task, Boolean resume, List<String> heldBy, String readProblem) {
    }

    /** Réponse de {@code start_task} et {@code complete_task}. */
    public record TaskAnswer(TaskInfo task, String jiraSync, String message, Boolean alreadyDone) {
    }

    private final TaskDesk desk;
    private final TaskSync sync;
    private final TaskJira jira;
    private final AgentConnections connections;
    private final AgentJournal journal;

    public AgentTasks(TaskDesk desk, TaskSync sync, TaskJira jira, AgentConnections connections, AgentJournal journal) {
        this.desk = desk;
        this.sync = sync;
        this.jira = jira;
        this.connections = connections;
        this.journal = journal;
    }

    /** Tâches créées d'une feature, relues dans Jira (FR-009, FR-019) ; sans appel s'il n'y en a pas. */
    public FeatureTasks forFeature(UUID featureId, String connection) {
        boolean problem = desk.locks.with(featureId, () -> sync.refresh(featureId));
        List<SpecTask> tasks = desk.tasks.tasks(featureId);
        return new FeatureTasks(infos(tasks, connection), TaskProgress.done(tasks), TaskProgress.total(tasks),
                desk.tasks.tracking(featureId), problem);
    }

    public NextAnswer next(String agentName, String connection, UUID featureId) {
        Session session = feature(featureId, connection);
        return desk.locks.with(featureId, () -> {
            boolean problem = sync.refresh(featureId);
            requireTracking(featureId);
            List<SpecTask> tasks = desk.tasks.tasks(featureId);
            TaskProgress.Next next = TaskProgress.next(tasks, connection, connections::alive);
            String read = problem ? "JIRA_UNREACHABLE" : null;
            NextAnswer answer = switch (next) {
                case TaskProgress.Next.Task t -> new NextAnswer("TASK", info(t.task(), tasks, connection), t.resume(),
                        null, read);
                case TaskProgress.Next.NoneLeft n -> new NextAnswer("NONE_LEFT", null, null, null, read);
                case TaskProgress.Next.AllHeld h -> new NextAnswer("ALL_HELD", null, null, h.heldBy(), read);
            };
            journal.recordTask(agentName, AgentOperation.NEXT_TASK, session.projectId(), featureId,
                    answer.task() == null ? null : answer.task().key());
            return answer;
        });
    }

    public TaskAnswer start(String agentName, String connection, UUID featureId, String key) {
        Session session = feature(featureId, connection);
        Holder holder = new Holder(connection, agentName, desk.clock.now());
        return desk.locks.with(featureId, () -> {
            sync.refresh(featureId);
            requireTracking(featureId);
            List<SpecTask> tasks = desk.tasks.tasks(featureId);
            SpecTask task = byKey(tasks, key);
            if (task.status() == TaskStatus.IN_PROGRESS) {
                if (task.holder() != null && task.holder().connection().equals(connection)) {
                    return answer(task, tasks, connection, null);
                }
                if (task.holder() == null || connections.alive(task.holder().connection())) {
                    String who = task.holder() == null ? "quelqu'un, dans Jira" : task.holder().agentName();
                    throw new DomainException(ErrorCode.TASK_HELD, "La tâche " + key + " est déjà prise par " + who
                            + ". Demandez la prochaine tâche.");
                }
                // connexion fermée : la tâche est reprise, déjà en cours dans Jira
                SpecTask resumed = task.started(holder, task.pendingTarget() == TaskStatus.IN_PROGRESS);
                return save(agentName, session, AgentOperation.START_TASK, resumed, connection);
            }
            if (!TaskProgress.startable(tasks, task)) {
                throw new DomainException(ErrorCode.TASK_NOT_READY, task.status() == TaskStatus.DONE
                        ? "La tâche " + key + " est déjà terminée."
                        : task.status() == TaskStatus.MISSING ? "La sous-tâche " + key + " est introuvable dans Jira."
                        : "Les tâches préalables de " + key + " ne sont pas terminées.");
            }
            TaskJira.Result result = jira.moveTo(desk.endpoint(featureId), key, StatusCategory.IN_PROGRESS);
            return save(agentName, session, AgentOperation.START_TASK,
                    task.started(holder, result == TaskJira.Result.UNREACHABLE), connection);
        });
    }

    public TaskAnswer complete(String agentName, String connection, UUID featureId, String key, String report) {
        Session session = feature(featureId, connection);
        if (report != null && report.strip().length() > SpecTask.MAX_REPORT) {
            throw new DomainException(ErrorCode.REPORT_TOO_LONG);
        }
        return desk.locks.with(featureId, () -> {
            sync.refresh(featureId);
            requireTracking(featureId);
            List<SpecTask> tasks = desk.tasks.tasks(featureId);
            SpecTask task = byKey(tasks, key);
            if (task.status() == TaskStatus.DONE) {
                TaskAnswer done = answer(task, tasks, connection, null);
                return new TaskAnswer(done.task(), done.jiraSync(), "La tâche " + key + " est déjà terminée.", true);
            }
            if (task.status() != TaskStatus.IN_PROGRESS || task.holder() == null
                    || !task.holder().connection().equals(connection)) {
                throw new DomainException(ErrorCode.TASK_NOT_READY,
                        "La tâche " + key + " n'est pas en cours pour cette conversation : prenez-la d'abord.");
            }
            TaskJira.Result moved = jira.moveTo(desk.endpoint(featureId), key, StatusCategory.DONE);
            SpecTask done;
            String message = null;
            if (moved == TaskJira.Result.UNREACHABLE) {
                done = task.completed(report, true);
            } else {
                done = task.completed(report, false);
                if (done.report() != null) {
                    try {
                        String wiki = desk.markup.toWiki("Compte rendu de " + task.holder().agentName() + " : "
                                + done.report());
                        if (jira.comment(desk.endpoint(featureId), key, wiki) == TaskJira.Result.UNREACHABLE) {
                            done = task.completed(report, true).transitionSynced();
                        }
                    } catch (DomainException refused) {
                        message = "La tâche est terminée, mais Jira a refusé le compte rendu : " + refused.userMessage();
                    }
                }
            }
            TaskAnswer answer = save(agentName, session, AgentOperation.COMPLETE_TASK, done, connection);
            return message == null ? answer : new TaskAnswer(answer.task(), answer.jiraSync(), message, null);
        });
    }

    private TaskAnswer save(String agentName, Session session, AgentOperation operation, SpecTask task,
            String connection) {
        desk.tasks.update(task);
        journal.recordTask(agentName, operation, session.projectId(), session.id(), task.jiraKey());
        sync.applyStatus(session.id());
        List<SpecTask> tasks = desk.tasks.tasks(session.id());
        return answer(task, tasks, connection, task.isPending() ? PENDING_MESSAGE : null);
    }

    private TaskAnswer answer(SpecTask task, List<SpecTask> tasks, String connection, String message) {
        TaskInfo info = info(task, tasks, connection);
        return new TaskAnswer(info, info.jiraSync(), message, null);
    }

    /** La feature est visible des outils de tâches hors brouillon ; la connexion est obligatoire (R4). */
    private Session feature(UUID featureId, String connection) {
        if (connection == null || connection.isBlank()) {
            throw new DomainException(ErrorCode.BRIDGE_OUTDATED);
        }
        desk.sessions.implementation(featureId).filter(i -> i.status() != ImplementationStatus.DRAFT)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        return desk.sessions.find(featureId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    private void requireTracking(UUID featureId) {
        if (desk.tasks.tracking(featureId) == TaskTracking.SUSPENDED) {
            throw new DomainException(ErrorCode.TRACKING_SUSPENDED, "Le suivi des tâches de cette feature est suspendu "
                    + "par l'utilisateur : n'avancez pas de tâche avant qu'il le reprenne.");
        }
    }

    private static SpecTask byKey(List<SpecTask> tasks, String key) {
        return tasks.stream().filter(t -> t.isCreated() && t.jiraKey().equals(key)).findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Aucune tâche " + key + " dans cette feature."));
    }

    private List<TaskInfo> infos(List<SpecTask> tasks, String connection) {
        return tasks.stream().filter(SpecTask::isCreated).map(t -> info(t, tasks, connection)).toList();
    }

    private TaskInfo info(SpecTask t, List<SpecTask> tasks, String connection) {
        Map<UUID, SpecTask> byId = tasks.stream().collect(Collectors.toMap(SpecTask::id, Function.identity()));
        List<String> deps = t.dependsOn().stream().map(byId::get).filter(d -> d != null && d.isCreated())
                .map(SpecTask::jiraKey).toList();
        boolean mine = t.holder() != null && t.holder().connection().equals(connection);
        String description = desk.tasks.descriptions(t.sessionId()).get(t.id());
        return new TaskInfo(t.jiraKey(), t.title(), t.outcome(), description, t.position(), deps, t.status(),
                t.holder() == null ? null : t.holder().agentName(), mine, t.isPending() ? "PENDING" : "OK", t.report());
    }
}
