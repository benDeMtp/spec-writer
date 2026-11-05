package dev.specwriter.application.search;

import dev.specwriter.application.jira.JiraAccess;
import dev.specwriter.application.project.CurrentProject;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.JiraIssueSummary;
import dev.specwriter.domain.jira.JiraSearchPage;
import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.port.IssueTracker;
import dev.specwriter.domain.port.LinkedTicketRepository;
import dev.specwriter.domain.port.LocalSearch;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Recherche dans spec-writer et dans Jira, en une liste unique (FR-039 à FR-045). Un élément lié à
 * un ticket n'apparaît qu'une fois, avec la provenance {@code BOTH}. Jira n'est interrogé qu'avec le
 * consentement {@code JIRA}, et son absence ou sa panne n'empêche pas les résultats locaux.
 */
public class UnifiedSearch {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 200;
    /** Éléments par page, toutes sources confondues (FR-021a de 002). */
    public static final int PAGE_SIZE = 25;

    public enum Provenance { SPEC_WRITER, JIRA, BOTH }

    public enum JiraStatus { OK, NOT_CONFIGURED, UNAVAILABLE, TOKEN_REJECTED, CONSENT_REQUIRED }

    /**
     * {@code id} : identifiant de session, ou clé Jira pour un ticket non lié. {@code latestVersion} :
     * dernière version de la session, pour ouvrir directement sa spécification (FR-043).
     */
    public record Item(String id, String title, Provenance provenance, String jiraKey, UUID sessionId,
            Integer latestVersion, Instant updatedAt, String excerpt) {
    }

    /**
     * Une page de la liste unique (research.md R9) : {@code position} « 26 à 50 sur 120 » ;
     * {@code nextCursor} nul en fin de liste ; {@code jiraError} présent si Jira n'a pas répondu, la
     * page ne portant alors que sur spec-writer et le curseur Jira n'avançant pas (FR-022).
     */
    public record Page(List<Item> items, String position, boolean approximateTotal, String nextCursor,
            JiraStatus jiraStatus, String jiraError) {

        public Page {
            items = List.copyOf(items);
        }
    }

    private final LocalSearch local;
    private final SpecificationRepository versions;
    private final LinkedTicketRepository tickets;
    private final IssueTracker tracker;
    private final JiraAccess jira;
    private final ProjectRepository projects;
    private final Duration jiraTimeout;
    private final SearchCursor cursors = new SearchCursor();

    public UnifiedSearch(LocalSearch local, SpecificationRepository versions, LinkedTicketRepository tickets,
            IssueTracker tracker, JiraAccess jira, ProjectRepository projects, Duration jiraTimeout) {
        this.local = local;
        this.versions = versions;
        this.tickets = tickets;
        this.tracker = tracker;
        this.jira = jira;
        this.projects = projects;
        this.jiraTimeout = jiraTimeout;
    }

    /**
     * Page suivant {@code cursor} (la première s'il est nul) : au plus 25 éléments de chaque source
     * sont lus à partir de leurs décalages, fusionnés par date de modification décroissante, et
     * coupés à 25. Un ticket lié à une session qui correspond n'est montré qu'une fois, côté local.
     */
    public Page run(String query, String cursor) {
        String q = query == null ? "" : query.strip();
        if (q.length() < MIN_LENGTH || q.length() > MAX_LENGTH) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "La recherche doit faire entre 2 et 200 caractères.");
        }
        SearchCursor.State state = cursors.decode(cursor, q);
        UUID project = CurrentProject.resolve(projects).id();
        Map<String, LinkedJiraTicket> byKey = new HashMap<>();
        Map<UUID, LinkedJiraTicket> bySession = new HashMap<>();
        tickets.all().forEach(t -> {
            byKey.put(t.issueKey(), t);
            bySession.put(t.sessionId(), t);
        });

        List<LocalSearch.Hit> hits = local.byDate(project, q, state.localOffset(), PAGE_SIZE);
        int localTotal = local.count(project, q);
        JiraOutcome outcome = searchJira(q, state.jiraOffset());
        boolean jiraOk = outcome.status() == JiraStatus.OK;

        List<Item> items = new ArrayList<>();
        int li = 0;
        int ji = 0;
        while (items.size() < PAGE_SIZE && (li < hits.size() || ji < outcome.issues().size())) {
            LocalSearch.Hit hit = li < hits.size() ? hits.get(li) : null;
            JiraIssueSummary issue = ji < outcome.issues().size() ? outcome.issues().get(ji) : null;
            if (issue == null || hit != null && !hit.updatedAt().isBefore(dateOf(issue))) {
                items.add(localItem(hit, bySession.get(hit.sessionId())));
                li++;
                continue;
            }
            ji++;
            LinkedJiraTicket t = byKey.get(issue.key());
            if (t != null && local.matches(project, q, t.sessionId())) {
                // déjà dans la liste, ou à venir, côté local
                continue;
            }
            items.add(jiraItem(issue, t));
        }

        int localNext = state.localOffset() + li;
        int jiraNext = state.jiraOffset() + ji;
        boolean jiraFailed = outcome.status() == JiraStatus.UNAVAILABLE || outcome.status() == JiraStatus.TOKEN_REJECTED;
        boolean more = localNext < localTotal || jiraOk && jiraNext < outcome.total() || jiraFailed;
        int shown = state.shown() + items.size();
        int total = Math.max(localTotal + (jiraOk ? outcome.total() : 0), shown);
        boolean approximate = jiraFailed || jiraOk && outcome.total() > 0 && localTotal > 0 && !byKey.isEmpty();
        String next = more ? cursors.encode(new SearchCursor.State(localNext, jiraOk ? jiraNext : state.jiraOffset(), shown), q)
                : null;
        return new Page(items, position(state.shown(), items.size(), total, approximate), approximate, next,
                outcome.status(), jiraError(outcome.status()));
    }

    private Item localItem(LocalSearch.Hit hit, LinkedJiraTicket t) {
        return new Item(hit.sessionId().toString(), hit.title(), t == null ? Provenance.SPEC_WRITER : Provenance.BOTH,
                t == null ? null : t.issueKey(), hit.sessionId(), latest(hit.sessionId()), hit.updatedAt(), hit.excerpt());
    }

    private Item jiraItem(JiraIssueSummary issue, LinkedJiraTicket t) {
        if (t == null) {
            return new Item(issue.key(), issue.summary(), Provenance.JIRA, issue.key(), null, null, issue.updated(), null);
        }
        return new Item(t.sessionId().toString(), issue.summary(), Provenance.BOTH, issue.key(), t.sessionId(),
                latest(t.sessionId()), issue.updated(), null);
    }

    /** Un ticket sans date passe après les éléments datés. */
    private static Instant dateOf(JiraIssueSummary issue) {
        return issue.updated() == null ? Instant.MIN : issue.updated();
    }

    /** « 26 à 50 sur 120 » ; « sur environ 120 » si le total est approché. */
    static String position(int before, int count, int total, boolean approximate) {
        if (count == 0) {
            return before == 0 ? "Aucun résultat" : "Aucun autre résultat";
        }
        return (before + 1) + " à " + (before + count) + " sur " + (approximate ? "environ " : "") + total;
    }

    private static String jiraError(JiraStatus status) {
        return switch (status) {
            case UNAVAILABLE -> "Jira n'a pas répondu : seuls les résultats spec-writer sont affichés.";
            case TOKEN_REJECTED -> "Jira a refusé le jeton : seuls les résultats spec-writer sont affichés.";
            default -> null;
        };
    }

    private record JiraOutcome(JiraStatus status, List<JiraIssueSummary> issues, int total) {

        static JiraOutcome of(JiraStatus status) {
            return new JiraOutcome(status, List.of(), 0);
        }
    }

    private JiraOutcome searchJira(String q, int startAt) {
        if (!jira.configured()) {
            return JiraOutcome.of(JiraStatus.NOT_CONFIGURED);
        }
        if (!jira.consented()) {
            return JiraOutcome.of(JiraStatus.CONSENT_REQUIRED);
        }
        Optional<JiraAccess.Target> target = jira.targetForCurrentProject();
        if (target.isEmpty()) {
            return JiraOutcome.of(JiraStatus.TOKEN_REJECTED);
        }
        JiraAccess.Target t = target.get();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        Future<JiraSearchPage> future = executor.submit(
                () -> tracker.searchText(t.endpoint(), t.projectKey(), q, startAt, PAGE_SIZE));
        try {
            JiraSearchPage result = future.get(jiraTimeout.toMillis(), TimeUnit.MILLISECONDS);
            return new JiraOutcome(JiraStatus.OK, result.issues(), result.total());
        } catch (TimeoutException e) {
            future.cancel(true);
            return JiraOutcome.of(JiraStatus.UNAVAILABLE);
        } catch (ExecutionException e) {
            boolean rejected = e.getCause() instanceof IssueTrackerException ite
                    && ite.kind() == IssueTrackerException.Kind.UNAUTHORIZED;
            return JiraOutcome.of(rejected ? JiraStatus.TOKEN_REJECTED : JiraStatus.UNAVAILABLE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return JiraOutcome.of(JiraStatus.UNAVAILABLE);
        } finally {
            executor.shutdownNow();
        }
    }

    private Integer latest(UUID sessionId) {
        int n = versions.lastNumber(sessionId);
        return n == 0 ? null : n;
    }
}
