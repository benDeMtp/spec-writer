package dev.specwriter.application.conversation;

import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.conversation.CitedSource;
import dev.specwriter.domain.conversation.Conversation;
import dev.specwriter.domain.conversation.ConversationMessage;
import dev.specwriter.domain.conversation.EstablishedPoint;
import dev.specwriter.domain.conversation.MessageRole;
import dev.specwriter.domain.conversation.NeedDraft;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.ConversationLlm;
import dev.specwriter.domain.port.ConversationRepository;
import dev.specwriter.domain.port.ModelCapacities;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.session.Language;
import dev.specwriter.domain.session.LanguageDetector;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Propose un besoin et des points établis à partir d'une conversation (010, US2). Rien n'est
 * enregistré : l'utilisateur relit le brouillon avant de démarrer la spécification. Un point qui
 * s'appuie sur une source que la conversation n'a pas citée est écarté.
 */
public class DraftNeed {

    private static final Logger LOG = Logger.getLogger(DraftNeed.class);

    private final ProjectRepository projects;
    private final ConversationRepository conversations;
    private final ConversationLlm llm;
    private final ModelCapacities capacities;
    private final ConsentPolicy consent;
    private final ConversationHistory history;

    public DraftNeed(ProjectRepository projects, ConversationRepository conversations, ConversationLlm llm,
            ModelCapacities capacities, ConsentPolicy consent, ClockPort clock) {
        this.history = new ConversationHistory(conversations, llm, clock);
        this.projects = projects;
        this.conversations = conversations;
        this.llm = llm;
        this.capacities = capacities;
        this.consent = consent;
    }

    public NeedDraft draft(UUID conversationId) {
        Conversation conversation = conversations.find(conversationId)
                .orElseThrow(() -> new DomainException(ErrorCode.CONVERSATION_NOT_FOUND));
        List<ConversationMessage> messages = conversations.messages(conversationId);
        if (messages.stream().noneMatch(m -> m.role() == MessageRole.USER)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Discutez d'abord du besoin avec l'agent : la conversation est vide.");
        }
        consent.requireGranted(ConsentService.COPILOT);
        Project project = projects.find(conversation.projectId())
                .orElseThrow(() -> new DomainException(ErrorCode.PROJECT_NOT_FOUND));
        Map<String, CitedSource> cited = new LinkedHashMap<>();
        messages.stream().filter(m -> m.role() == MessageRole.AGENT)
                .flatMap(m -> conversations.sources(m.id()).stream())
                .forEach(c -> cited.putIfAbsent(c.ref(), c));
        Language language = LanguageDetector.detect(messages.stream().filter(m -> m.role() == MessageRole.USER)
                .map(ConversationMessage::text).reduce("", (a, b) -> a + "\n" + b));
        ConversationHistory.Prepared history = this.history.prepare(conversationId, messages, Integer.MAX_VALUE,
                language, capacities.budget(), phase -> { });
        NeedDraft proposed = llm.draftNeed(new ConversationLlm.DraftRequest(language, project.context().markdown(),
                history.memory(), history.exchanges(), List.copyOf(cited.values())));
        List<EstablishedPoint> kept = proposed.points().stream()
                .filter(p -> p.basis() == EstablishedPoint.Basis.USER || cited.containsKey(p.sourceRef()))
                .limit(NeedDraft.MAX_POINTS).toList();
        LOG.infof("Brouillon de besoin : %d point(s) proposé(s), %d gardé(s)", proposed.points().size(), kept.size());
        return new NeedDraft(proposed.need(), kept);
    }
}
