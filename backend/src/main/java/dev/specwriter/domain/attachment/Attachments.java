package dev.specwriter.domain.attachment;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.DocumentationReader.UnreadReason;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Règles des documents joints (009, data-model.md) : nom, format, plafond, doublons, refus. */
public final class Attachments {

    /** Documents joints au plus par session (clarification 5). */
    public static final int MAX_PER_SESSION = 5;
    public static final int MAX_FILE_NAME = 255;

    private Attachments() {
    }

    /** Nom du fichier, sans chemin (le navigateur n'en donne pas, mais rien n'est supposé). */
    public static String fileName(String raw) {
        String name = raw == null ? "" : raw.strip();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(slash + 1).strip();
        if (name.isEmpty() || name.length() > MAX_FILE_NAME) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Le nom du fichier doit faire entre 1 et 255 caractères.");
        }
        return name;
    }

    public static AttachmentFormat format(String fileName) {
        return AttachmentFormat.of(fileName).orElseThrow(() -> new DomainException(ErrorCode.UNSUPPORTED_FORMAT));
    }

    /** Titre du document, sinon le nom du fichier sans extension. */
    public static String title(String title, String fileName) {
        if (title != null && !title.isBlank()) {
            return title.strip();
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    public static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    /** Refuse un document au-delà du plafond, ou déjà joint (même contenu ou même nom). */
    public static void requireRoom(List<Attachment> existing, String fileName, String contentHash) {
        if (existing.size() >= MAX_PER_SESSION) {
            throw new DomainException(ErrorCode.ATTACHMENT_LIMIT);
        }
        String name = fileName.toLowerCase(Locale.ROOT);
        if (existing.stream().anyMatch(a -> a.contentHash().equals(contentHash)
                || a.fileName().toLowerCase(Locale.ROOT).equals(name))) {
            throw new DomainException(ErrorCode.ATTACHMENT_DUPLICATE);
        }
    }

    public static int nextOrdinal(List<Attachment> existing) {
        return existing.stream().mapToInt(Attachment::ordinal).max().orElse(0) + 1;
    }

    /** Refus de lecture, avec la raison de la 006 et sans rien du contenu. */
    public static DomainException unreadable(UnreadReason reason) {
        String why = switch (reason) {
            case PROTECTED -> "Le document est protégé par un mot de passe ou chiffré.";
            case NO_TEXT -> "Le document ne contient pas de texte lisible (document scanné ?).";
            case TOO_LARGE -> "Le document dépasse les limites de lecture (20 Mo, 200 000 caractères de texte).";
            case UNAVAILABLE -> "La lecture du document a pris trop longtemps.";
            default -> "Le document est illisible ou endommagé.";
        };
        return new DomainException(ErrorCode.DOCUMENT_UNREADABLE, why);
    }
}
