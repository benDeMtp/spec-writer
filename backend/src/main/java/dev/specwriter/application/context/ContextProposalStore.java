package dev.specwriter.application.context;

import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.DocumentationReader.Unread;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.port.TextDiff;
import dev.specwriter.domain.project.Project;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fiches de contexte proposées par l'IA, en attente de décision (data-model.md de 002,
 * ContextProposal). Gardées en mémoire, jamais enregistrées ; une proposition oubliée disparaît au
 * bout de 30 minutes ou à la fermeture de l'outil.
 */
public class ContextProposalStore {

    public static final Duration TTL = Duration.ofMinutes(30);

    /**
     * {@code baseContextHash} : hash de la fiche au moment de la proposition, qui doit être toujours
     * le même à l'acceptation. {@code sources} : les références citées par la fiche.
     */
    public record ContextProposal(UUID id, UUID projectId, String baseContextHash, String markdown, String unifiedDiff,
            List<String> sources, List<Unread> unread, Instant createdAt) {
    }

    private final Map<UUID, ContextProposal> proposals = new ConcurrentHashMap<>();
    private final TextDiff diff;
    private final IdGenerator ids;
    private final ClockPort clock;

    public ContextProposalStore(TextDiff diff, IdGenerator ids, ClockPort clock) {
        this.diff = diff;
        this.ids = ids;
        this.clock = clock;
    }

    public ContextProposal propose(Project project, String markdown, List<String> sources, List<Unread> unread) {
        purge();
        String unified = diff.unified(project.context().markdown(), markdown, "fiche actuelle", "proposition");
        ContextProposal p = new ContextProposal(ids.next(), project.id(), project.context().hash(), markdown, unified,
                List.copyOf(sources), List.copyOf(unread), clock.now());
        proposals.put(p.id(), p);
        return p;
    }

    /** Retire et renvoie la proposition, si elle existe, appartient au projet et n'a pas expiré. */
    public Optional<ContextProposal> take(UUID projectId, UUID proposalId) {
        purge();
        ContextProposal p = proposals.get(proposalId);
        if (p == null || !p.projectId().equals(projectId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(proposals.remove(proposalId));
    }

    /** « Garder la mienne » : la proposition est oubliée. */
    public void discard(UUID projectId, UUID proposalId) {
        take(projectId, proposalId);
    }

    private void purge() {
        Instant limit = clock.now().minus(TTL);
        proposals.values().removeIf(p -> !p.createdAt().isAfter(limit));
    }
}
