package fr.kawamind.application.conversation;

import fr.kawamind.application.attachment.AttachmentDrafts;
import fr.kawamind.domain.attachment.Attachment;
import fr.kawamind.domain.attachment.AttachmentDraft;
import fr.kawamind.domain.attachment.Attachments;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.AttachmentReader;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.ConversationAttachmentRepository;
import fr.kawamind.domain.port.ConversationRepository;
import fr.kawamind.domain.port.IdGenerator;
import java.util.List;
import java.util.UUID;

/**
 * Documents joints d'une conversation (010, US3) : mêmes règles que ceux d'une spécification (009,
 * 5 au plus, doublons refusés, texte seul gardé). L'agent les lit en tête de ses sources ; une
 * spécification démarrée depuis la conversation en reçoit une copie.
 */
public class ConversationAttachments {

    private final ConversationRepository conversations;
    private final ConversationAttachmentRepository attachments;
    private final AttachmentReader reader;
    private final ClockPort clock;
    private final IdGenerator ids;

    public ConversationAttachments(ConversationRepository conversations, ConversationAttachmentRepository attachments,
            AttachmentReader reader, ClockPort clock, IdGenerator ids) {
        this.conversations = conversations;
        this.attachments = attachments;
        this.reader = reader;
        this.clock = clock;
        this.ids = ids;
    }

    public List<Attachment.View> list(UUID conversationId) {
        require(conversationId);
        return attachments.views(conversationId);
    }

    public Attachment.View add(UUID conversationId, String fileName, byte[] content) {
        require(conversationId);
        String name = Attachments.fileName(fileName);
        Attachments.format(name);
        List<Attachment> existing = attachments.list(conversationId);
        if (existing.size() >= Attachments.MAX_PER_SESSION) {
            throw new DomainException(ErrorCode.ATTACHMENT_LIMIT);
        }
        AttachmentReader.Reading reading = AttachmentDrafts.read(reader, name, content);
        Attachment added = Attachment.create(ids.next(), conversationId, Attachments.nextOrdinal(existing),
                reading.title(), name, reading.text(), clock.now());
        Attachments.requireRoom(existing, name, added.contentHash());
        attachments.add(added);
        return added.view();
    }

    public void remove(UUID conversationId, UUID attachmentId) {
        require(conversationId);
        if (!attachments.remove(conversationId, attachmentId)) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
    }

    /** Copies à rattacher à une spécification : nouveaux identifiants, même texte, même ordre. */
    public List<AttachmentDraft> copies(UUID conversationId) {
        return attachments.list(conversationId).stream().map(a -> new AttachmentDraft(ids.next(), a.title(),
                a.fileName(), a.format(), a.text(), clock.now())).toList();
    }

    private void require(UUID conversationId) {
        conversations.find(conversationId).orElseThrow(() -> new DomainException(ErrorCode.CONVERSATION_NOT_FOUND));
    }
}
