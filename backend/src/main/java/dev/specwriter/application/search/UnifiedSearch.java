package dev.specwriter.application.search;

import dev.specwriter.application.jira.JiraAccess;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.JiraIssueSummary;
import dev.specwriter.domain.jira.JiraSearchPage;
import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.port.IssueTracker;
import dev.specwriter.domain.port.LinkedTicketRepository;
import dev.specwriter.domain.port.LocalSearch;
import dev.specwriter.domain.port.SpecificationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    public static final int PAGE_SIZE = 20;
    static final int LOCAL_LIMIT = 50;

    public enum Provenance { SPEC_WRITER, JIRA, BOTH }

    public enum JiraStatus { OK, NOT_CONFIGURED, UNAVAILABLE, TOKEN_REJECTED, CONSENT_REQUIRED }

    /**
     * {@code id} : identifiant de session, ou clé Jira pour un ticket non lié. {@code latestVersion} :
     * dernière version de la session, pour ouvrir directement sa spécification (FR-043).
     */
    public record Item(String id, String title, Provenance provenance, String jiraKey, UUID sessionId,
            Integer latestVersion, Instant updatedAt, String excerpt) {
    }

    /** {@code jiraTotal} : nombre de tickets Jira trouvés, pour paginer (0 si Jira n'a pas répondu). */
    public record Result(List<Item> items, JiraStatus jiraStatus, int jiraTotal) {

        public Result {
            items = List.copyOf(items);
        }
    }

    private final LocalSearch local;
    private final SpecificationRepository versions;
    private final LinkedTicketRepository tickets;
    private final IssueTracker tracker;
    private final JiraAccess jira;
    private final Duration jiraTimeout;

    public UnifiedSearch(LocalSearch local, SpecificationRepository versions, LinkedTicketRepository tickets,
            IssueTracker tracker, JiraAccess jira, Duration jiraTimeout) {
        this.local = local;
        this.versions = versions;
        this.tickets = tickets;
        this.tracker = tracker;
        this.jira = jira;
        this.jiraTimeout = jiraTimeout;
    }

    /**
     * La page 0 contient les résultats locaux (50 au plus) et la première page Jira ; les pages
     * suivantes ne portent que sur Jira.
     */
    public Result run(String query, int page) {
        String q = query == null ? "" : query.strip();
        if (q.length() < MIN_LENGTH || q.length() > MAX_LENGTH || page < 0) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "La recherche doit faire entre 2 et 200 caractères.");
        }
        Map<String, LinkedJiraTicket> byKey = new HashMap<>();
        Map<UUID, LinkedJiraTicket> bySession = new HashMap<>();
        tickets.all().forEach(t -> {
            byKey.put(t.issueKey(), t);
            bySession.put(t.sessionId(), t);
        });

        Map<String, Item> items = new LinkedHashMap<>();
        if (page == 0) {
            for (LocalSearch.Hit hit : local.search(q, LOCAL_LIMIT)) {
                LinkedJiraTicket t = bySession.get(hit.sessionId());
                items.put(hit.sessionId().toString(), new Item(hit.sessionId().toString(), hit.title(),
                        t == null ? Provenance.SPEC_WRITER : Provenance.BOTH, t == null ? null : t.issueKey(),
                        hit.sessionId(), latest(hit.sessionId()), hit.updatedAt(), hit.excerpt()));
            }
        }

        JiraOutcome outcome = searchJira(q, page);
        for (JiraIssueSummary issue : outcome.issues()) {
            LinkedJiraTicket t = byKey.get(issue.key());
            if (t == null) {
                items.put(issue.key(), new Item(issue.key(), issue.summary(), Provenance.JIRA, issue.key(), null, null,
                        issue.updated(), null));
            } else {
                Item known = items.get(t.sessionId().toString());
                Instant updated = latest(known == null ? null : known.updatedAt(), issue.updated());
                items.put(t.sessionId().toString(), new Item(t.sessionId().toString(),
                        known == null ? issue.summary() : known.title(), Provenance.BOTH, issue.key(), t.sessionId(),
                        latest(t.sessionId()), updated, known == null ? null : known.excerpt()));
            }
        }
        List<Item> sorted = new ArrayList<>(items.values());
        sorted.sort(Comparator.comparing(Item::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return new Result(sorted, outcome.status(), outcome.total());
    }

    private record JiraOutcome(JiraStatus status, List<JiraIssueSummary> issues, int total) {

        static JiraOutcome of(JiraStatus status) {
            return new JiraOutcome(status, List.of(), 0);
        }
    }

    private JiraOutcome searchJira(String q, int page) {
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
                () -> tracker.searchText(t.endpoint(), t.projectKey(), q, page * PAGE_SIZE, PAGE_SIZE));
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

    private static Instant latest(Instant a, Instant b) {
        if (a == null) {
            return b;
        }
        return b == null || a.isAfter(b) ? a : b;
    }
}
