package dev.specwriter.domain.attachment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Document joint à une spécification (009, data-model.md) : seul son texte extrait est gardé, avec
 * la session ; ni le fichier ni son chemin. Le texte n'apparaît jamais dans {@link #toString()}.
 */
public record Attachment(UUID id, UUID sessionId, int ordinal, String title, String fileName,
        AttachmentFormat format, String contentHash, String text, Instant addedAt) {

    /** Préfixe de la référence d'un document joint dans la connaissance transmise. */
    public static final String REF_PREFIX = "joint:";

    /** Ce que voient les écrans : jamais le texte. */
    public record View(UUID id, String title, String fileName, AttachmentFormat format, int chars, Instant addedAt) {
    }

    public Attachment {
        Objects.requireNonNull(id);
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(text);
        fileName = Attachments.fileName(fileName);
        format = format == null ? Attachments.format(fileName) : format;
        title = Attachments.title(title, fileName);
        contentHash = contentHash == null ? Attachments.hash(text) : contentHash;
    }

    public static Attachment create(UUID id, UUID sessionId, int ordinal, String title, String fileName, String text,
            Instant addedAt) {
        return new Attachment(id, sessionId, ordinal, title, fileName, null, null, text, addedAt);
    }

    public int chars() {
        return text.length();
    }

    /** Référence dans la connaissance et dans les sources de la spécification. */
    public String ref() {
        return REF_PREFIX + fileName;
    }

    public View view() {
        return new View(id, title, fileName, format, chars(), addedAt);
    }

    @Override
    public String toString() {
        return "Attachment[" + id + ", " + fileName + ", " + chars() + " caractères]";
    }
}
