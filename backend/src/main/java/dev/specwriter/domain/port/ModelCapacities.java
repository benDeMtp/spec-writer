package dev.specwriter.domain.port;

import dev.specwriter.domain.knowledge.KnowledgeBudget;
import dev.specwriter.domain.settings.ModelCapacity;
import java.util.List;
import java.util.OptionalInt;

/** Capacité des modèles Copilot, rangée à la connexion (008, research.md R1). */
public interface ModelCapacities {

    /** Limite de prompt du modèle choisi ; vide si le modèle ou sa limite est inconnu. */
    OptionalInt promptTokens();

    /** Capacités des modèles autorisés pour le compte. */
    List<ModelCapacity> all();

    /** Place de la connaissance avec le modèle choisi. */
    default KnowledgeBudget budget() {
        return KnowledgeBudget.forPromptTokens(promptTokens());
    }

    /** Aucun modèle connu : le budget d'aujourd'hui. */
    ModelCapacities UNKNOWN = new ModelCapacities() {
        @Override
        public OptionalInt promptTokens() {
            return OptionalInt.empty();
        }

        @Override
        public List<ModelCapacity> all() {
            return List.of();
        }
    };
}
