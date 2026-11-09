package dev.specwriter.domain.attachment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Document joint sur l'écran du besoin, avant le démarrage de la session (009, research.md R3) :
 * gardé en mémoire seulement, rattaché à la session au démarrage.
 */
public record AttachmentDraft(UUID id, String title, String fileName, AttachmentFormat format, String text,
        Instant createdAt) {

    public AttachmentDraft {
        Objects.requireNonNull(id);
        Objects.requireNonNull(text);
        fileName = Attachments.fileName(fileName);
        format = format == null ? Attachments.format(fileName) : format;
        title = Attachments.title(title, fileName);
    }

    public String contentHash() {
        return Attachments.hash(text);
    }

    public String ref() {
        return Attachment.REF_PREFIX + fileName;
    }

    /** Le document rattaché à une session. */
    public Attachment attach(UUID sessionId, int ordinal, Instant now) {
        return new Attachment(id, sessionId, ordinal, title, fileName, format, null, text, now);
    }

    public Attachment.View view() {
        return new Attachment.View(id, title, fileName, format, text.length(), createdAt);
    }

    @Override
    public String toString() {
        return "AttachmentDraft[" + id + ", " + fileName + ", " + text.length() + " caractères]";
    }
}
