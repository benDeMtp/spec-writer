package dev.specwriter.application.knowledge;

import dev.specwriter.application.jira.JiraAccess;
import dev.specwriter.application.knowledge.SynchronizeKnowledge.Mode;
import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.jira.IssueStatus;
import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.llm.RelatedIssuesJudgement;
import dev.specwriter.domain.llm.RelatedIssuesRequest;
import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.domain.port.IssueTracker;
import dev.specwriter.domain.port.KnowledgeIndex;
import dev.specwriter.domain.port.KnowledgeSourceRepository;
import dev.specwriter.domain.port.LlmClient;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.session.Session;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Tickets du projet qui traitent du même problème que le besoin (FR-034, research.md R23) : les 20
 * meilleurs de l'index, jugés par l'IA (5 au plus, avec la raison), ou les 3 meilleurs sans raison
 * si Copilot n'est pas disponible. Le statut est relu dans Jira avant d'être montré.
 */
public class FindRelatedIssues {

    static final int CANDIDATES = 20;
    static final int FALLBACK = 3;

    private static final Logger LOG = Logger.getLogger(FindRelatedIssues.class);

    private final KnowledgeIndex index;
    private final EmbeddingPort embedding;
    private final LlmClient llm;
    private final ConsentPolicy consent;
    private final JiraAccess jira;
    private final IssueTracker tracker;
    private final Indexations indexations;
    private final KnowledgeSourceRepository sources;

    public FindRelatedIssues(KnowledgeIndex index, EmbeddingPort embedding, LlmClient llm, ConsentPolicy consent,
            JiraAccess jira, IssueTracker tracker, Indexations indexations, KnowledgeSourceRepository sources) {
        this.index = index;
        this.embedding = embedding;
        this.llm = llm;
        this.consent = consent;
        this.jira = jira;
        this.tracker = tracker;
        this.indexations = indexations;
        this.sources = sources;
    }

    public RelatedIssues find(Session session, Project project) {
        // l'index se met à jour sans attendre ; la recherche porte sur ce qu'il contient déjà
        indexations.trigger(project.id(), Mode.INCREMENTAL);
        if (index.count(project.id(), KnowledgeKind.TICKET) == 0) {
            return RelatedIssues.UNAVAILABLE;
        }
        boolean meaning = embedding.state() == EmbeddingPort.State.READY;
        List<KnowledgeItem> candidates = candidates(project.id(), session.need().text());
        boolean partial = !meaning || building(project.id());
        RelatedIssues.Status status = partial ? RelatedIssues.Status.PARTIAL : RelatedIssues.Status.READY;
        if (candidates.isEmpty()) {
            return new RelatedIssues(status, List.of(), null);
        }
        Map<String, String> retained = judge(session, candidates);
        Optional<JiraAccess.Target> target = jira.consented() ? jira.target() : Optional.empty();
        List<RelatedIssues.Issue> issues = new ArrayList<>();
        for (Map.Entry<String, String> kept : retained.entrySet()) {
            KnowledgeItem item = candidates.stream().filter(c -> c.ref().equals(kept.getKey())).findFirst().orElseThrow();
            fresh(project, item, kept.getValue(), target).ifPresent(issues::add);
        }
        return new RelatedIssues(status, issues, null);
    }

    /**
     * L'index se construit : indexation complète, ou première synchronisation, en cours. La mise à
     * jour incrémentale lancée à chaque ouverture d'un besoin ne rend pas les résultats partiels.
     */
    private boolean building(UUID projectId) {
        return indexations.current(projectId).filter(s -> s.status() == Indexations.Status.RUNNING)
                .map(s -> s.mode() == Mode.FULL || sources.find(projectId).syncedAt() == null).orElse(false);
    }

    /**
     * Tickets candidats du besoin, les plus proches d'abord, soumis ensuite au jugement de l'IA ;
     * mesurés sur le jeu de référence (research.md R27).
     */
    public List<KnowledgeItem> candidates(UUID projectId, String need) {
        boolean meaning = embedding.state() == EmbeddingPort.State.READY;
        float[] vector = meaning ? embedding.query(need) : null;
        return index.search(projectId, need, vector, meaning ? embedding.modelId() : null, EnumSet.of(KnowledgeKind.TICKET),
                CANDIDATES).stream().map(KnowledgeIndex.Hit::item).toList();
    }

    /** Clé et raison des tickets retenus, dans l'ordre ; les 3 meilleurs sans raison à défaut de Copilot. */
    private Map<String, String> judge(Session session, List<KnowledgeItem> candidates) {
        Map<String, String> retained = new LinkedHashMap<>();
        if (consent.isGranted(ConsentService.COPILOT)) {
            try {
                RelatedIssuesJudgement judgement = llm.judgeRelatedIssues(new RelatedIssuesRequest(session.language(),
                        session.need().text(), candidates.stream().map(FindRelatedIssues::candidate).toList()));
                for (RelatedIssuesJudgement.Related r : judgement.related()) {
                    boolean known = candidates.stream().anyMatch(c -> c.ref().equals(r.key()));
                    if (known && retained.size() < RelatedIssuesJudgement.MAX_RELATED) {
                        retained.putIfAbsent(r.key(), r.reason());
                    }
                }
                return retained;
            } catch (DomainException e) {
                LOG.warnf("Tickets proches : jugement de l'IA indisponible (%s), les meilleurs résultats sont montrés",
                        e.code());
            }
        }
        candidates.stream().limit(FALLBACK).forEach(c -> retained.put(c.ref(), null));
        return retained;
    }

    private static RelatedIssuesRequest.Candidate candidate(KnowledgeItem item) {
        String body = item.body();
        String excerpt = body.length() <= RelatedIssuesRequest.MAX_EXCERPT ? body
                : body.substring(0, RelatedIssuesRequest.MAX_EXCERPT);
        return new RelatedIssuesRequest.Candidate(item.ref(), item.title(), item.status(), excerpt);
    }

    /** Statut relu dans Jira ; un ticket supprimé est retiré de l'index et n'est pas montré. */
    private Optional<RelatedIssues.Issue> fresh(Project project, KnowledgeItem item, String reason,
            Optional<JiraAccess.Target> target) {
        String url = target.map(t -> t.issueUrl(item.ref())).orElse(null);
        if (target.isPresent()) {
            try {
                IssueStatus status = tracker.issueStatus(target.get().endpoint(), item.ref());
                return Optional.of(new RelatedIssues.Issue(item.ref(), item.title(), status.status(),
                        status.statusCategory(), status.closed(), true,
                        status.updated() == null ? item.sourceUpdatedAt() : status.updated(), reason, url));
            } catch (IssueTrackerException e) {
                if (e.kind() == IssueTrackerException.Kind.NOT_FOUND) {
                    index.delete(project.id(), KnowledgeIndex.Key.of(item));
                    return Optional.empty();
                }
                LOG.infof("Tickets proches : statut de %s non relu (%s), statut indexé montré", item.ref(), e.kind());
            }
        }
        return Optional.of(new RelatedIssues.Issue(item.ref(), item.title(), item.status(), item.statusCategory(),
                item.closed(), false, item.sourceUpdatedAt(), reason, url));
    }
}
