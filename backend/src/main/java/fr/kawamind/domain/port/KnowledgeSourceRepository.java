package fr.kawamind.domain.port;

import fr.kawamind.domain.knowledge.KnowledgeSource;
import java.util.UUID;

/** Sources et repères de l'index de connaissance d'un projet (colonnes du projet, V10). */
public interface KnowledgeSourceRepository {

    /** Les repères du projet, vides s'il n'en a pas encore. */
    KnowledgeSource find(UUID projectId);

    void save(KnowledgeSource source);
}
