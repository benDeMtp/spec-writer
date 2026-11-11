package dev.specwriter.domain.port;

import dev.specwriter.domain.sources.PerimeterAnalysis;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Analyses de périmètre des réponses de l'agent (013, research.md R9) ; supprimées avec la conversation. */
public interface PerimeterRepository {

    /** Enregistre l'analyse d'une réponse, ou la remplace (même ordinal), par exemple une fois qualifiée. */
    void save(UUID messageId, PerimeterAnalysis analysis);

    /** Analyses d'une réponse, dans l'ordre des ordinaux. */
    List<PerimeterAnalysis> list(UUID messageId);

    /** Dernière analyse de la conversation, dans l'ordre des messages puis des ordinaux. */
    Optional<PerimeterAnalysis> last(UUID conversationId);

    /** Retire les analyses d'une réponse relancée. */
    void clear(UUID messageId);
}
