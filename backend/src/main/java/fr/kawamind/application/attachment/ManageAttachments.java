package fr.kawamind.application.attachment;

import fr.kawamind.domain.attachment.Attachment;
import fr.kawamind.domain.attachment.Attachments;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.AttachmentReader;
import fr.kawamind.domain.port.AttachmentRepository;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.IdGenerator;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.session.SpecificationLock;
import java.util.List;
import java.util.UUID;

/**
 * Documents joints d'une session démarrée (009, US3) : liste sans le texte, ajout (lecture, plafond
 * de 5, doublons refusés) et retrait. Une feature implémentée est verrouillée ; les versions
 * existantes ne changent pas, seule la génération suivante tient compte des documents.
 */
public class ManageAttachments {

    private final SessionRepository sessions;
    private final AttachmentRepository attachments;
    private final AttachmentReader reader;
    private final ClockPort clock;
    private final IdGenerator ids;

    public ManageAttachments(SessionRepository sessions, AttachmentRepository attachments, AttachmentReader reader,
            ClockPort clock, IdGenerator ids) {
        this.sessions = sessions;
        this.attachments = attachments;
        this.reader = reader;
        this.clock = clock;
        this.ids = ids;
    }

    /** Documents de la session dans l'ordre d'ajout, sans leur texte. */
    public List<Attachment.View> list(UUID sessionId) {
        requireSession(sessionId);
        return attachments.views(sessionId);
    }

    public Attachment.View add(UUID sessionId, String fileName, byte[] content) {
        requireSession(sessionId);
        SpecificationLock.requireEditable(sessions, sessionId);
        String name = Attachments.fileName(fileName);
        Attachments.format(name);
        List<Attachment> existing = attachments.list(sessionId);
        if (existing.size() >= Attachments.MAX_PER_SESSION) {
            // refusé avant de lire le fichier
            throw new DomainException(ErrorCode.ATTACHMENT_LIMIT);
        }
        AttachmentReader.Reading reading = AttachmentDrafts.read(reader, name, content);
        Attachment added = Attachment.create(ids.next(), sessionId, Attachments.nextOrdinal(existing), reading.title(),
                name, reading.text(), clock.now());
        Attachments.requireRoom(existing, name, added.contentHash());
        attachments.add(added);
        return added.view();
    }

    public void remove(UUID sessionId, UUID attachmentId) {
        requireSession(sessionId);
        SpecificationLock.requireEditable(sessions, sessionId);
        if (!attachments.remove(sessionId, attachmentId)) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
    }

    private void requireSession(UUID sessionId) {
        sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }
}
