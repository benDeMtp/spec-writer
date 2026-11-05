package dev.specwriter.domain.port;

import dev.specwriter.domain.project.DocSource;
import java.util.Optional;

/**
 * Fichiers d'une source de documentation de type dossier (feature 004, research.md R3). Un chemin
 * qui sort du dossier déclaré, un dossier ou un fichier absent ne donnent rien.
 */
public interface DocumentFiles {

    /** Contenu d'un fichier, au plus quelques mégaoctets. */
    record DocumentFile(String path, byte[] content) {
    }

    /** Vrai si {@code path}, relatif au dossier de {@code folder}, y désigne un fichier. */
    boolean exists(DocSource folder, String path);

    Optional<DocumentFile> read(DocSource folder, String path);
}
