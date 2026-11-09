package dev.specwriter.domain.attachment;

import java.util.Locale;
import java.util.Optional;

/** Formats d'un document joint : ceux que lisent les lecteurs de la 006. */
public enum AttachmentFormat {
    DOCX,
    XLSX,
    PDF,
    HTML;

    /** Format d'après l'extension du nom du fichier ; vide si elle n'est pas prise en charge. */
    public static Optional<AttachmentFormat> of(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dot < 0) {
            return Optional.empty();
        }
        return switch (fileName.substring(dot + 1).toLowerCase(Locale.ROOT)) {
            case "docx" -> Optional.of(DOCX);
            case "xlsx" -> Optional.of(XLSX);
            case "pdf" -> Optional.of(PDF);
            case "html", "htm" -> Optional.of(HTML);
            default -> Optional.empty();
        };
    }
}
