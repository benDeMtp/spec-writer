package dev.specwriter.application.conversation;

import dev.specwriter.domain.attachment.Attachment;
import dev.specwriter.domain.conversation.Conversation;
import dev.specwriter.domain.conversation.SpecificationDiscussion;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.AttachmentRepository;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.ConversationRepository;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationDiscussionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.session.SpecificationLock;
import dev.specwriter.domain.specification.SpecificationVersion;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Discussion d'une spécification (011, US1, US3) : une conversation rattachée, sur la dernière version.
 * Une seule discussion suivie par spécification ; « Nouvelle discussion » en démarre une autre, et
 * l'ancienne reste dans la liste des conversations. Une spécification implémentée ne se discute pas.
 */
public class DiscussSpecification {

    static final String PREFIX = "Discussion : ";

    /** Discussion suivie, vue par l'écran de la spécification. */
    public record View(UUID conversationId, String specificationName, int version) {
    }

    /** Ce que l'agent reçoit de la spécification discutée, à chaque tour. */
    public record Context(UUID conversationId, UUID sessionId, String specificationName, int version, String markdown,
            List<Attachment> attachments) {

        public Context {
            attachments = List.copyOf(attachments);
        }

        @Override
        public String toString() {
            return "Context[" + sessionId + ", v" + version + ", " + markdown.length() + " caractères]";
        }
    }

    private final SessionRepository sessions;
    private final SpecificationRepository versions;
    private final ConversationRepository conversations;
    private final SpecificationDiscussionRepository discussions;
    private final ClockPort clock;
    private final IdGenerator ids;
    private final AttachmentRepository attachments;

    public DiscussSpecification(SessionRepository sessions, SpecificationRepository versions,
            ConversationRepository conversations, SpecificationDiscussionRepository discussions, ClockPort clock,
            IdGenerator ids) {
        this(sessions, versions, conversations, discussions, clock, ids, null);
    }

    /** Avec les documents joints aux spécifications (009), transmis à l'agent comme documents désignés. */
    public DiscussSpecification(SessionRepository sessions, SpecificationRepository versions,
            ConversationRepository conversations, SpecificationDiscussionRepository discussions, ClockPort clock,
            IdGenerator ids, AttachmentRepository attachments) {
        this.attachments = attachments;
        this.sessions = sessions;
        this.versions = versions;
        this.conversations = conversations;
        this.discussions = discussions;
        this.clock = clock;
        this.ids = ids;
    }

    /** Discussion suivie, sans la créer. */
    public Optional<View> current(UUID sessionId) {
        return discussions.current(sessionId).flatMap(d -> latest(sessionId)
                .map(v -> new View(d.conversationId(), d.specificationName(), v.number())));
    }

    /** Discussion suivie, créée si la spécification n'en a pas encore. */
    public synchronized View open(UUID sessionId) {
        Session session = editable(sessionId);
        SpecificationVersion latest = requireVersion(sessionId);
        SpecificationDiscussion d = discussions.current(sessionId).orElseGet(() -> start(session));
        return new View(d.conversationId(), d.specificationName(), latest.number());
    }

    /** Nouvelle discussion suivie ; la précédente reste dans la liste des conversations. */
    public synchronized View fresh(UUID sessionId) {
        Session session = editable(sessionId);
        SpecificationVersion latest = requireVersion(sessionId);
        discussions.current(sessionId).ifPresent(d -> discussions.save(d.retired()));
        SpecificationDiscussion d = start(session);
        return new View(d.conversationId(), d.specificationName(), latest.number());
    }

    /**
     * Spécification discutée par la conversation, sur sa dernière version ; vide pour une
     * conversation ordinaire, ou dont la spécification a été supprimée.
     */
    public Optional<Context> of(UUID conversationId) {
        return discussions.ofConversation(conversationId).filter(SpecificationDiscussion::attached)
                .flatMap(d -> latest(d.sessionId()).map(v -> new Context(conversationId, d.sessionId(),
                        d.specificationName(), v.number(), v.markdown(),
                        attachments == null ? List.of() : attachments.list(d.sessionId()))));
    }

    /** Une discussion n'accepte rien sur une spécification implémentée. */
    public void requireEditable(UUID conversationId) {
        discussions.ofConversation(conversationId).filter(SpecificationDiscussion::attached)
                .ifPresent(d -> SpecificationLock.requireEditable(sessions, d.sessionId()));
    }

    private SpecificationDiscussion start(Session session) {
        String title = PREFIX + session.name();
        Conversation c = new Conversation(ids.next(), session.projectId(),
                title.length() > Conversation.MAX_NAME ? title.substring(0, Conversation.MAX_NAME) : title, clock.now(),
                clock.now());
        conversations.save(c);
        SpecificationDiscussion d = SpecificationDiscussion.start(c.id(), session.id(), session.name(), clock.now());
        discussions.save(d);
        return d;
    }

    private Session editable(UUID sessionId) {
        Session session = sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        SpecificationLock.requireEditable(sessions, sessionId);
        return session;
    }

    private SpecificationVersion requireVersion(UUID sessionId) {
        return latest(sessionId).orElseThrow(() -> new DomainException(ErrorCode.VALIDATION_FAILED,
                "Générez d'abord une spécification."));
    }

    private Optional<SpecificationVersion> latest(UUID sessionId) {
        return versions.list(sessionId).stream().max(Comparator.comparingInt(SpecificationVersion::number));
    }
}
