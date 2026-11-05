package dev.specwriter.application.specification;

import dev.specwriter.domain.knowledge.ProjectKnowledge;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.port.TextDiff;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.specification.MarkdownRenderer;
import dev.specwriter.domain.specification.SpecificationContent;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.VersionOrigin;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Propositions de l'IA en attente de validation (FR-044). Gardées en mémoire, jamais enregistrées :
 * une proposition non acceptée disparaît au bout de 30 minutes ou à la fermeture de l'outil.
 */
public class ProposalStore {

    public static final Duration TTL = Duration.ofMinutes(30);

    /**
     * {@code jiraUpdated} et {@code jiraDescriptionHash} : état Jira lu pour une fusion de conflit,
     * qui devient la référence à l'acceptation ; nuls pour une modification simple. La connaissance
     * utilisée est retenue sur la version acceptée (FR-013d, research.md R24 de 002).
     */
    public record Proposal(UUID id, UUID sessionId, int baseVersion, SpecificationContent content, String markdown,
            String unifiedDiff, VersionOrigin origin, Instant createdAt, Instant jiraUpdated, String jiraDescriptionHash,
            ProjectKnowledge knowledge) {

        public Instant contextUpdatedAt() {
            return knowledge.sheetUpdatedAt();
        }
    }

    private final Map<UUID, Proposal> proposals = new ConcurrentHashMap<>();
    private final TextDiff diff;
    private final IdGenerator ids;
    private final ClockPort clock;

    public ProposalStore(TextDiff diff, IdGenerator ids, ClockPort clock) {
        this.diff = diff;
        this.ids = ids;
        this.clock = clock;
    }

    public Proposal propose(Session session, SpecificationVersion base, SpecificationContent content, VersionOrigin origin,
            Instant jiraUpdated, String jiraDescriptionHash, ProjectKnowledge knowledge) {
        purge();
        String markdown = MarkdownRenderer.render(content, session.language(), knowledge.selection());
        String unified = diff.unified(base.markdown(), markdown, "v" + base.number(), "proposition");
        Proposal p = new Proposal(ids.next(), session.id(), base.number(), content, markdown, unified, origin, clock.now(),
                jiraUpdated, jiraDescriptionHash, knowledge);
        proposals.put(p.id(), p);
        return p;
    }

    /** Retire et renvoie la proposition, si elle existe, appartient à la session et n'a pas expiré. */
    public Optional<Proposal> take(UUID sessionId, UUID proposalId) {
        purge();
        Proposal p = proposals.get(proposalId);
        if (p == null || !p.sessionId().equals(sessionId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(proposals.remove(proposalId));
    }

    /** Retire les propositions de la session : sa version ou la session elle-même est supprimée (research.md R8). */
    public void discard(UUID sessionId) {
        proposals.values().removeIf(p -> p.sessionId().equals(sessionId));
    }

    private void purge() {
        Instant limit = clock.now().minus(TTL);
        proposals.values().removeIf(p -> !p.createdAt().isAfter(limit));
    }
}
