package dev.specwriter.domain.port;

import dev.specwriter.domain.knowledge.ArchitectureModel;
import java.util.List;

/**
 * Lecture d'un workspace Structurizr depuis un dossier local (FR-038, research.md R21) : éléments
 * propres et communs, relations et déploiements. Aucun script ni plugin n'est exécuté : un
 * {@code !script} est ignoré, avec un avertissement.
 */
public interface ArchitectureReader {

    /** Erreur d'analyse, avec le fichier et la ligne quand l'analyseur les donne. */
    record Problem(String file, Integer line, String message) {
    }

    /**
     * Modèle lu (vide en cas d'erreur), fichiers lus et leur empreinte combinée ; {@code warnings} :
     * ce qui a été ignoré sans empêcher la lecture, comme un {@code !script}.
     */
    record Result(ArchitectureModel model, List<String> files, String hash, Problem problem, List<Problem> warnings) {

        public Result {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        public Result(ArchitectureModel model, List<String> files, String hash, Problem problem) {
            this(model, files, hash, problem, List.of());
        }

        public boolean failed() {
            return problem != null;
        }
    }

    /**
     * @param workspace chemin du fichier {@code workspace.dsl}
     * @param ownership identifiant du projet dans la propriété {@code ownership}, ou nul
     */
    Result read(String workspace, String ownership);
}
