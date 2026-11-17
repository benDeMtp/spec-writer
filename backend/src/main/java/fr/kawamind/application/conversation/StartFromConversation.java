package fr.kawamind.application.conversation;

import fr.kawamind.application.session.SessionSnapshot;
import fr.kawamind.application.session.StartSession;
import fr.kawamind.domain.attachment.AttachmentDraft;
import fr.kawamind.domain.conversation.Conversation;
import fr.kawamind.domain.conversation.NeedDraft;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.ConversationRepository;
import fr.kawamind.domain.port.PerimeterRepository;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.sources.PerimeterSnapshot;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Démarre une spécification à partir du brouillon relu (010, US2) : besoin, points établis devenus
 * réponses, documents joints de la conversation. La conversation et la spécification sont liées.
 */
public class StartFromConversation {

    private final ConversationRepository conversations;
    private final ProjectRepository projects;
    private final StartSession startSession;
    /** Documents joints à la conversation, copiés dans la spécification (US3). */
    private final Function<UUID, List<AttachmentDraft>> joined;
    /** La dernière analyse de périmètre est copiée dans la session (013, US5, research.md R11). */
    private final PerimeterRepository perimeters;
    private final ClockPort clock;

    public StartFromConversation(ConversationRepository conversations, ProjectRepository projects,
            StartSession startSession, Function<UUID, List<AttachmentDraft>> joined) {
        this(conversations, projects, startSession, joined, null, null);
    }

    public StartFromConversation(ConversationRepository conversations, ProjectRepository projects,
            StartSession startSession, Function<UUID, List<AttachmentDraft>> joined, PerimeterRepository perimeters,
            ClockPort clock) {
        this.perimeters = perimeters;
        this.clock = clock;
        this.conversations = conversations;
        this.projects = projects;
        this.startSession = startSession;
        this.joined = joined;
    }

    public SessionSnapshot start(UUID conversationId, NeedDraft draft) {
        Conversation conversation = conversations.find(conversationId)
                .orElseThrow(() -> new DomainException(ErrorCode.CONVERSATION_NOT_FOUND));
        Project project = projects.find(conversation.projectId())
                .orElseThrow(() -> new DomainException(ErrorCode.PROJECT_NOT_FOUND));
        SessionSnapshot started = startSession.fromConversation(draft.need(), draft.points(), project,
                joined.apply(conversationId));
        conversations.link(conversationId, started.session().id());
        if (perimeters != null) {
            perimeters.last(conversationId).ifPresent(a -> perimeters.saveSnapshot(started.session().id(),
                    PerimeterSnapshot.of(a), clock.now()));
        }
        return started;
    }
}
