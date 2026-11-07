package dev.specwriter.domain.port;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * État des fichiers d'un dossier de documents à leur dernière lecture (006, research.md R3) :
 * taille et date de modification, pour ne relire, donc ne faire télécharger, que ce qui a changé.
 * Jamais de contenu.
 */
public interface DocumentFileStates {

    record FileState(long size, Instant modifiedAt) {
    }

    /** États par chemin relatif. */
    Map<String, FileState> of(UUID sourceId);

    /** Remplace tous les états de la source. */
    void replace(UUID sourceId, Map<String, FileState> states);
}
