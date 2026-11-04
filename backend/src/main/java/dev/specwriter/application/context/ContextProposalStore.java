package dev.specwriter.application.context;

import dev.specwriter.domain.llm.ProjectContextDraft;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.DocumentationReader.Unread;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.port.TextDiff;
import dev.specwriter.domain.project.ContextQuestion;
import dev.specwriter.domain.project.ContextSheet;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectContext;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Propositions de fiches de contexte, gardées en mémoire 30 minutes comme les propositions
 * d'édition de 001 (FR-013a, research.md R4 et R16 de 002). Chaque fiche s'accepte séparément.
 */
public class ContextProposalStore {

    public static final Duration TTL = Duration.ofMinutes(30);

    /** Fiche proposée : hash de la fiche au moment de la proposition, Markdown, diff avec l'actuelle. */
    public record ProposedSheet(String baseHash, String markdown, String unifiedDiff, boolean changed, boolean accepted) {

        ProposedSheet asAccepted() {
            return new ProposedSheet(baseHash, markdown, unifiedDiff, changed, true);
        }
    }

    public enum QuestionState { OPEN, ANSWERED, SKIPPED }

    /** Question de clarification de la proposition ; {@code number} une fois répondue ou passée (FR-013h). */
    public record ProposalQuestion(UUID id, ContextSheet sheet, String label, String rationale, List<String> suggestions,
            QuestionState state, Integer number, String answer, Instant askedAt) {

        ProposalQuestion recorded(ContextQuestion q) {
            return new ProposalQuestion(id, sheet, label, rationale, suggestions,
                    q.state() == ContextQuestion.State.SKIPPED ? QuestionState.SKIPPED : QuestionState.ANSWERED, q.number(),
                    q.answer(), askedAt);
        }
    }

    /** {@code sources} : les références citées par les fiches (page, ticket ou {@code Q-n}). */
    public record ContextProposal(UUID id, UUID projectId, ProposedSheet functional, ProposedSheet technical,
            List<String> sources, List<Unread> unread, List<ProposalQuestion> questions, Instant createdAt) {

        public ProposedSheet sheet(ContextSheet sheet) {
            return sheet == ContextSheet.FUNCTIONAL ? functional : technical;
        }

        public Optional<ProposalQuestion> question(UUID questionId) {
            return questions.stream().filter(q -> q.id().equals(questionId)).findFirst();
        }

        ContextProposal withQuestion(ProposalQuestion updated) {
            return new ContextProposal(id, projectId, functional, technical, sources, unread,
                    questions.stream().map(q -> q.id().equals(updated.id()) ? updated : q).toList(), createdAt);
        }

        ContextProposal withAccepted(Set<ContextSheet> sheets) {
            return new ContextProposal(id, projectId,
                    sheets.contains(ContextSheet.FUNCTIONAL) ? functional.asAccepted() : functional,
                    sheets.contains(ContextSheet.TECHNICAL) ? technical.asAccepted() : technical, sources, unread,
                    questions, createdAt);
        }

        boolean fullyAccepted() {
            return functional.accepted() && technical.accepted();
        }
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

    public ContextProposal propose(Project project, ProjectContextDraft draft, List<String> sources, List<Unread> unread) {
        purge();
        Instant now = clock.now();
        List<ProposalQuestion> questions = draft.questions().stream()
                .map(q -> new ProposalQuestion(ids.next(), q.sheet(), q.label(), q.rationale(), q.suggestions(),
                        QuestionState.OPEN, null, null, now))
                .toList();
        ContextProposal p = new ContextProposal(ids.next(), project.id(),
                sheet(project.context(ContextSheet.FUNCTIONAL), draft.functional(), "fonctionnelle"),
                sheet(project.context(ContextSheet.TECHNICAL), draft.technical(), "technique"), List.copyOf(sources),
                List.copyOf(unread), questions, now);
        proposals.put(p.id(), p);
        return p;
    }

    private ProposedSheet sheet(ProjectContext current, String markdown, String label) {
        String unified = diff.unified(current.markdown(), markdown, "fiche " + label + " actuelle", "proposition");
        return new ProposedSheet(current.hash(), markdown, unified, !markdown.equals(current.markdown()), false);
    }

    /** La proposition, si elle existe, appartient au projet et n'a pas expiré. */
    public Optional<ContextProposal> find(UUID projectId, UUID proposalId) {
        purge();
        return Optional.ofNullable(proposals.get(proposalId)).filter(p -> p.projectId().equals(projectId));
    }

    /** Fiches acceptées : la proposition est oubliée quand les deux le sont. */
    void markAccepted(ContextProposal proposal, Set<ContextSheet> sheets) {
        ContextProposal updated = proposal.withAccepted(EnumSet.copyOf(sheets));
        if (updated.fullyAccepted()) {
            proposals.remove(proposal.id());
        } else {
            proposals.put(proposal.id(), updated);
        }
    }

    void updateQuestion(ContextProposal proposal, ProposalQuestion question) {
        proposals.computeIfPresent(proposal.id(), (id, p) -> p.withQuestion(question));
    }

    /** « Garder les miennes », ou remplacement par une révision : la proposition est oubliée. */
    public void discard(UUID projectId, UUID proposalId) {
        find(projectId, proposalId).ifPresent(p -> proposals.remove(p.id()));
    }

    private void purge() {
        Instant limit = clock.now().minus(TTL);
        proposals.values().removeIf(p -> !p.createdAt().isAfter(limit));
    }
}
