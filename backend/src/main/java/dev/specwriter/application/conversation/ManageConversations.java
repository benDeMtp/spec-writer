package dev.specwriter.application.conversation;

import dev.specwriter.application.sources.PerimeterView;
import dev.specwriter.application.jira.JiraAccess;
import dev.specwriter.application.specification.OperationRegistry;
import dev.specwriter.domain.conversation.CitedSource;
import dev.specwriter.domain.conversation.Conversation;
import dev.specwriter.domain.conversation.ConversationMemory;
import dev.specwriter.domain.conversation.ConversationMessage;
import dev.specwriter.domain.conversation.MessageRole;
import dev.specwriter.domain.conversation.MessageState;
import dev.specwriter.domain.conversation.SearchStep;
import dev.specwriter.domain.conversation.Thinking;
import dev.specwriter.domain.conversation.SourceKind;
import dev.specwriter.domain.conversation.SpecificationDiscussion;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ConversationRepository;
import dev.specwriter.domain.port.PerimeterRepository;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationDiscussionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /** {@code specificationVersion} : version de la spécification discutée transmise pour ce tour (011). */
    public record MessageView(UUID id, int ordinal, MessageRole role, String text, MessageState state,
            boolean historyTruncated, Instant createdAt, List<SearchStep> steps, List<SourceView> sources,
            Thinking thinking, Integer specificationVersion, List<PerimeterView> perimeters) {
    }

    /** Spécification discutée (011) ; {@code sessionId} nul : la spécification a été supprimée. */
    public record SpecificationRef(UUID sessionId, String name) {
    }

    /** Conversation de la liste, avec la spécification qu'elle discute s'il y en a une (011). */
    public record Summary(UUID id, UUID projectId, String name, Instant createdAt, Instant updatedAt,
            SpecificationRef specification) {
    }

    public record SessionLink(UUID sessionId, String name) {
    }

    /** {@code memoryUntil} : les messages jusqu'à ce numéro d'ordre sont transmis résumés ; nul sans mémoire. */
    public record Detail(UUID id, UUID projectId, String name, Instant createdAt, Instant updatedAt,
            List<MessageView> messages, List<SessionLink> sessions, Integer memoryUntil,
            SpecificationRef specification) {
    }

    private final ProjectRepository projects;
    private final ConversationRepository conversations;
    private final SessionRepository sessions;
    private final JiraAccess jira;
    private final OperationRegistry operations;
    private final SpecificationDiscussionRepository discussions;
    /** Analyses de périmètre des réponses (013) ; nul : aucune. */
    private final PerimeterRepository perimeters;

    public ManageConversations(ProjectRepository projects, ConversationRepository conversations,
            SessionRepository sessions, JiraAccess jira, OperationRegistry operations) {
        this(projects, conversations, sessions, jira, operations, null);
    }

    /** Avec les discussions des spécifications (011). */
    public ManageConversations(ProjectRepository projects, ConversationRepository conversations,
            SessionRepository sessions, JiraAccess jira, OperationRegistry operations,
            SpecificationDiscussionRepository discussions) {
        this(projects, conversations, sessions, jira, operations, discussions, null);
    }

    /** Avec les analyses de périmètre des réponses (013). */
    public ManageConversations(ProjectRepository projects, ConversationRepository conversations,
            SessionRepository sessions, JiraAccess jira, OperationRegistry operations,
            SpecificationDiscussionRepository discussions, PerimeterRepository perimeters) {
        this.perimeters = perimeters;
        this.discussions = discussions;
        this.projects = projects;
        this.conversations = conversations;
        this.sessions = sessions;
        this.jira = jira;
        this.operations = operations;
    }

    /** Conversations du projet, la plus récente d'abord. */
    public List<Summary> list(UUID projectId) {
        projects.find(projectId).orElseThrow(() -> new DomainException(ErrorCode.PROJECT_NOT_FOUND));
        List<Conversation> all = conversations.list(projectId);
        Map<UUID, SpecificationDiscussion> discussed = discussions == null ? Map.of()
                : discussions.ofConversations(all.stream().map(Conversation::id).toList());
        return all.stream().map(c -> new Summary(c.id(), c.projectId(), c.name(), c.createdAt(), c.updatedAt(),
                ref(discussed.get(c.id())))).toList();
    }

    private static SpecificationRef ref(SpecificationDiscussion d) {
        return d == null ? null : new SpecificationRef(d.sessionId(), d.specificationName());
    }

    public Detail detail(UUID conversationId) {
        Conversation c = require(conversationId);
        List<MessageView> messages = conversations.messages(conversationId).stream()
                .map(m -> new MessageView(m.id(), m.ordinal(), m.role(), m.text(), m.state(), m.historyTruncated(),
                        m.createdAt(),
                        m.role() == MessageRole.AGENT ? conversations.steps(m.id()) : List.of(),
                        m.role() == MessageRole.AGENT ? conversations.sources(m.id()).stream().map(this::view).toList()
                                : List.of(),
                        m.role() == MessageRole.AGENT ? conversations.thinking(m.id()).orElse(null) : null,
                        m.specificationVersion(),
                        m.role() == MessageRole.AGENT && perimeters != null
                                ? perimeters.list(m.id()).stream().map(PerimeterView::of).toList() : List.of()))
                .toList();
        List<SessionLink> links = conversations.sessions(conversationId).stream()
                .map(id -> sessions.find(id).map(s -> new SessionLink(id, s.name())))
                .flatMap(Optional::stream).toList();
        return new Detail(c.id(), c.projectId(), c.name(), c.createdAt(), c.updatedAt(), messages, links,
                conversations.memory(conversationId).map(ConversationMemory::coveredUntil).orElse(null),
                discussions == null ? null : discussions.ofConversation(conversationId).map(ManageConversations::ref)
                        .orElse(null));
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
            case ARCH_ELEMENT, ATTACHMENT, DISCUSSION, MODULE -> null;
        };
    }

    /**
     * {@code spec:<session>@v<n>} → {@code /sessions/<session>/versions/<n>} ; une section de la
     * spécification discutée ({@code #<ancre>}, 011) garde son ancre.
     */
    static String specificationUrl(String ref) {
        if (!ref.startsWith(ConversationSearch.SPEC_PREFIX) || !ref.contains("@v")) {
            return null;
        }
        String body = ref.substring(ConversationSearch.SPEC_PREFIX.length());
        String anchor = "";
        int hash = body.indexOf('#');
        if (hash >= 0) {
            anchor = body.substring(hash);
            body = body.substring(0, hash);
        }
        int at = body.lastIndexOf("@v");
        return "/sessions/" + body.substring(0, at) + "/versions/" + body.substring(at + 2) + anchor;
    }

    private Conversation require(UUID conversationId) {
        return conversations.find(conversationId)
                .orElseThrow(() -> new DomainException(ErrorCode.CONVERSATION_NOT_FOUND));
    }
}
