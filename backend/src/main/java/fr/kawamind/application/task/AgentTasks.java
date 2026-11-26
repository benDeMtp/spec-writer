package fr.kawamind.application.task;

import fr.kawamind.application.agent.AgentJournal;
import fr.kawamind.domain.agent.AgentOperation;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.jira.LinkedJiraTicket;
import fr.kawamind.domain.port.AgentConnections;
import fr.kawamind.domain.session.ImplementationStatus;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.task.Holder;
import fr.kawamind.domain.task.SpecTask;
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
     * {@code description} : celle de la sous-tâche dans Jira, en Markdown, nulle si vide ;
     * {@code issueType} : libellé de son type dans Jira, nul tant qu'elle n'a pas été relue (022) ;
     * {@code jiraStatus} : vrai nom de son statut Jira, « Réalisée » par exemple (023, FR-013) ;
     * {@code parentKey} : ticket lié à la feature, sous lequel elle est créée (022, research R4).
     */
    public record TaskInfo(String key, String title, String outcome, String description, String issueType,
            String jiraStatus, String parentKey, int position, List<String> dependsOn,
            TaskStatus status, String heldBy, boolean heldByYou, String jiraSync, String report) {
    }

    /** Tâches d'une feature, pour {@code get_feature}. */
    public record FeatureTasks(List<TaskInfo> tasks, int done, int total, TaskTracking tracking, boolean readProblem) {
    }

    /** Réponse de {@code next_task} : {@code TASK}, {@code NONE_LEFT} ou {@code ALL_HELD}. */
    /** {@code branch} : consigne de branche d'une sous-tâche, avec la clé de son parent (022) ; nulle sinon. */
    public record NextAnswer(String outcome, TaskInfo task, Boolean resume, List<String> heldBy, String readProblem,
            String branch) {
    }

    /** Réponse de {@code start_task} et {@code complete_task}. */
    /** {@code branch} : consigne de branche, pour {@code start_task} seulement (022) ; nulle sinon. */
    /** {@code story} : ce qui est arrivé à la story de la feature (023) ; nul si elle n'est pas concernée. */
    public record TaskAnswer(TaskInfo task, String jiraSync, String message, Boolean alreadyDone, String branch,
            StoryMove story) {
    }

    private final TaskDesk desk;
    private final TaskSync sync;
    private final TaskJira jira;
    private final AgentConnections connections;
    private final AgentJournal journal;
    /** La story suit ses tâches (023, US4). */
    private final StoryFollow story;

    public AgentTasks(TaskDesk desk, TaskSync sync, TaskJira jira, AgentConnections connections, AgentJournal journal) {
        this(desk, sync, jira, connections, journal, new StoryFollow(desk, jira));
    }

    public AgentTasks(TaskDesk desk, TaskSync sync, TaskJira jira, AgentConnections connections, AgentJournal journal,
            StoryFollow story) {
        this.story = story;
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
                case TaskProgress.Next.Task t -> task(info(t.task(), tasks, connection), t.resume(), read);
                case TaskProgress.Next.NoneLeft n -> new NextAnswer("NONE_LEFT", null, null, null, read, null);
                case TaskProgress.Next.AllHeld h -> new NextAnswer("ALL_HELD", null, null, h.heldBy(), read, null);
            };
            journal.recordTask(agentName, AgentOperation.NEXT_TASK, session.projectId(), featureId,
                    answer.task() == null ? null : answer.task().key());
            return answer;
        });
    }

    public TaskAnswer start(String agentName, String connection, UUID featureId, String key) {
        Session session = feature(featureId, connection);
        Holder holder = new Holder(connection, agentName, desk.clock.now());
        TaskAnswer started = withBranch(desk.locks.with(featureId, () -> {
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
            // 023 : jamais un saut direct vers un statut fini du projet
            TaskJira.Result result = jira.start(desk.endpoint(featureId), key, desk.workflow(featureId)).result();
            return save(agentName, session, AgentOperation.START_TASK,
                    task.started(holder, result == TaskJira.Result.UNREACHABLE), connection);
        }));
        // 023 (US4) : la story encore « à faire » passe en cours
        return withStory(started, story.started(featureId));
    }

    public TaskAnswer complete(String agentName, String connection, UUID featureId, String key, String report) {
        Session session = feature(featureId, connection);
        if (report != null && report.strip().length() > SpecTask.MAX_REPORT) {
            throw new DomainException(ErrorCode.REPORT_TOO_LONG);
        }
        TaskAnswer finished = desk.locks.with(featureId, () -> {
            sync.refresh(featureId);
            requireTracking(featureId);
            List<SpecTask> tasks = desk.tasks.tasks(featureId);
            SpecTask task = byKey(tasks, key);
            if (task.status() == TaskStatus.DONE) {
                TaskAnswer done = answer(task, tasks, connection, null);
                return new TaskAnswer(done.task(), done.jiraSync(), "La tâche " + key + " est déjà terminée.", true, null,
                        null);
            }
            if (task.status() != TaskStatus.IN_PROGRESS || task.holder() == null
                    || !task.holder().connection().equals(connection)) {
                throw new DomainException(ErrorCode.TASK_NOT_READY,
                        "La tâche " + key + " n'est pas en cours pour cette conversation : prenez-la d'abord.");
            }
            // 023 : le statut d'arrivée réglé pour le projet, sinon la catégorie « terminé »
            TaskJira.Result moved = jira.moveTo(desk.endpoint(featureId), key, desk.workflow(featureId).completion());
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
            return message == null ? answer
                    : new TaskAnswer(answer.task(), answer.jiraSync(), message, null, null, answer.story());
        });
        if (Boolean.TRUE.equals(finished.alreadyDone()) || !allDone(featureId)) {
            return finished;
        }
        // 023 (US4) : la dernière tâche finie, la story passe au statut d'arrivée
        return withStory(finished, story.allDone(featureId));
    }

    private boolean allDone(UUID featureId) {
        List<SpecTask> created = desk.tasks.tasks(featureId).stream().filter(SpecTask::isCreated).toList();
        return !created.isEmpty() && created.stream().allMatch(t -> t.status() == TaskStatus.DONE);
    }

    private static TaskAnswer withStory(TaskAnswer answer, StoryMove moved) {
        return new TaskAnswer(answer.task(), answer.jiraSync(), answer.message(), answer.alreadyDone(), answer.branch(),
                moved);
    }

    /** Réponse de {@code next_task} pour une tâche, avec la consigne de branche d'une sous-tâche (022). */
    private static NextAnswer task(TaskInfo info, Boolean resume, String read) {
        return new NextAnswer("TASK", info, resume, null, read, branchRule(info.key(), info.parentKey()));
    }

    /** Réponse de {@code start_task}, avec la consigne de branche d'une sous-tâche (022). */
    private static TaskAnswer withBranch(TaskAnswer answer) {
        return new TaskAnswer(answer.task(), answer.jiraSync(), answer.message(), answer.alreadyDone(),
                branchRule(answer.task().key(), answer.task().parentKey()), answer.story());
    }

    /**
     * Consigne de branche d'une sous-tâche (022, research R6) : tout le travail des sous-tâches d'un
     * même parent se fait sur la branche de ce parent ; le nom n'est pas imposé. Nulle sans parent.
     */
    static String branchRule(String key, String parentKey) {
        if (parentKey == null) {
            return null;
        }
        return key + " est une sous-tâche de " + parentKey + " : si une branche est nécessaire, travaillez sur celle de "
                + parentKey + " (reprenez-la si elle existe). Toutes les sous-tâches de " + parentKey + " s'y font.";
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
        return new TaskAnswer(info, info.jiraSync(), message, null, null, null);
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
        if (tasks.isEmpty()) {
            return List.of();
        }
        Read read = read(tasks.getFirst().sessionId());
        return tasks.stream().filter(SpecTask::isCreated).map(t -> info(t, tasks, connection, read)).toList();
    }

    /** Ce que Jira a dit des tâches d'une feature, lu une fois par appel (V33, V34, 022). */
    private record Read(Map<UUID, String> descriptions, Map<UUID, String> types, String parentKey) {
    }

    /** Le ticket lié à la feature est le parent de ses tâches (022, research R4) ; nul sans ticket lié. */
    private Read read(UUID featureId) {
        return new Read(desk.tasks.descriptions(featureId), desk.tasks.issueTypes(featureId),
                desk.tickets.findBySession(featureId).map(LinkedJiraTicket::issueKey).orElse(null));
    }

    private TaskInfo info(SpecTask t, List<SpecTask> tasks, String connection) {
        return info(t, tasks, connection, read(t.sessionId()));
    }

    private TaskInfo info(SpecTask t, List<SpecTask> tasks, String connection, Read read) {
        Map<UUID, SpecTask> byId = tasks.stream().collect(Collectors.toMap(SpecTask::id, Function.identity()));
        List<String> deps = t.dependsOn().stream().map(byId::get).filter(d -> d != null && d.isCreated())
                .map(SpecTask::jiraKey).toList();
        boolean mine = t.holder() != null && t.holder().connection().equals(connection);
        return new TaskInfo(t.jiraKey(), t.title(), t.outcome(), read.descriptions().get(t.id()),
                read.types().get(t.id()), t.jiraStatusName(), read.parentKey(), t.position(), deps, t.status(),
                t.holder() == null ? null : t.holder().agentName(), mine, t.isPending() ? "PENDING" : "OK", t.report());
    }
}
