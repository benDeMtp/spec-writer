package dev.specwriter.application.conversation;

import dev.specwriter.application.jira.JiraAccess;
import dev.specwriter.application.specification.OperationRegistry;
import dev.specwriter.domain.conversation.CitedSource;
import dev.specwriter.domain.conversation.Conversation;
import dev.specwriter.domain.conversation.ConversationMessage;
import dev.specwriter.domain.conversation.MessageRole;
import dev.specwriter.domain.conversation.MessageState;
import dev.specwriter.domain.conversation.SearchStep;
import dev.specwriter.domain.conversation.SourceKind;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ConversationRepository;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.port.SessionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Conversations d'un projet (010, US1, US4) : liste, détail avec les étapes et les sources citées
 * (et leur adresse), renommage, suppression. Une conversation ne voit que son projet.
 */
public class ManageConversations {

    /** Source citée, avec l'adresse où l'ouvrir : ticket Jira, page, ADR, version d'une spécification. */
    public record SourceView(int n, SourceKind kind, String ref, String title, String url) {
    }

    public record MessageView(UUID id, MessageRole role, String text, MessageState state, boolean historyTruncated,
            Instant createdAt, List<SearchStep> steps, List<SourceView> sources) {
    }

    public record SessionLink(UUID sessionId, String name) {
    }

    public record Detail(UUID id, UUID projectId, String name, Instant createdAt, Instant updatedAt,
            List<MessageView> messages, List<SessionLink> sessions) {
    }

    private final ProjectRepository projects;
    private final ConversationRepository conversations;
    private final SessionRepository sessions;
    private final JiraAccess jira;
    private final OperationRegistry operations;

    public ManageConversations(ProjectRepository projects, ConversationRepository conversations,
            SessionRepository sessions, JiraAccess jira, OperationRegistry operations) {
        this.projects = projects;
        this.conversations = conversations;
        this.sessions = sessions;
        this.jira = jira;
        this.operations = operations;
    }

    /** Conversations du projet, la plus récente d'abord. */
    public List<Conversation> list(UUID projectId) {
        projects.find(projectId).orElseThrow(() -> new DomainException(ErrorCode.PROJECT_NOT_FOUND));
        return conversations.list(projectId);
    }

    public Detail detail(UUID conversationId) {
        Conversation c = require(conversationId);
        List<MessageView> messages = conversations.messages(conversationId).stream()
                .map(m -> new MessageView(m.id(), m.role(), m.text(), m.state(), m.historyTruncated(), m.createdAt(),
                        m.role() == MessageRole.AGENT ? conversations.steps(m.id()) : List.of(),
                        m.role() == MessageRole.AGENT ? conversations.sources(m.id()).stream().map(this::view).toList()
                                : List.of()))
                .toList();
        List<SessionLink> links = conversations.sessions(conversationId).stream()
                .map(id -> sessions.find(id).map(s -> new SessionLink(id, s.name())))
                .flatMap(Optional::stream).toList();
        return new Detail(c.id(), c.projectId(), c.name(), c.createdAt(), c.updatedAt(), messages, links);
    }

    public Conversation rename(UUID conversationId, String name) {
        Conversation renamed = require(conversationId).renamed(name);
        conversations.save(renamed);
        return renamed;
    }

    /** Supprime la conversation ; les spécifications démarrées depuis elle restent (FR-018). */
    public void delete(UUID conversationId) {
        require(conversationId);
        if (operations.running(conversationId)) {
            throw new DomainException(ErrorCode.OPERATION_IN_PROGRESS,
                    "L'agent répond encore : arrêtez sa réponse, puis supprimez la conversation.");
        }
        conversations.delete(conversationId);
    }

    /** Conversation dont vient une spécification, si elle existe encore. */
    public Optional<Conversation> origin(UUID sessionId) {
        return conversations.conversationOf(sessionId).flatMap(conversations::find);
    }

    private SourceView view(CitedSource s) {
        return new SourceView(s.n(), s.kind(), s.ref(), s.title(), url(s));
    }

    private String url(CitedSource s) {
        return switch (s.kind()) {
            case TICKET -> jira.target().map(t -> t.issueUrl(s.ref())).orElse(null);
            case DOC_SECTION, ADR -> s.ref().toLowerCase(Locale.ROOT).startsWith("http") ? s.ref() : null;
            case SPECIFICATION -> specificationUrl(s.ref());
            case ARCH_ELEMENT, ATTACHMENT -> null;
        };
    }

    /** {@code spec:<session>@v<n>} → {@code /sessions/<session>/versions/<n>}, si la session existe encore. */
    static String specificationUrl(String ref) {
        if (!ref.startsWith(ConversationSearch.SPEC_PREFIX) || !ref.contains("@v")) {
            return null;
        }
        String body = ref.substring(ConversationSearch.SPEC_PREFIX.length());
        int at = body.lastIndexOf("@v");
        return "/sessions/" + body.substring(0, at) + "/versions/" + body.substring(at + 2);
    }

    private Conversation require(UUID conversationId) {
        return conversations.find(conversationId)
                .orElseThrow(() -> new DomainException(ErrorCode.CONVERSATION_NOT_FOUND));
    }
}
