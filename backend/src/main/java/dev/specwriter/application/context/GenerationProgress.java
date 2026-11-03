package dev.specwriter.application.context;

/**
 * Suivi d'une génération de contexte (FR-013f, research.md R14) : étape, élément en cours de
 * lecture, avancement et erreurs. L'avancement est affiché, jamais journalisé.
 */
public interface GenerationProgress {

    enum Step { READING_DOCS, READING_TICKETS, SUMMARIZING, BUILDING }

    GenerationProgress NONE = new GenerationProgress() {
        @Override
        public void step(Step step, int total) {
            // aucun suivi
        }

        @Override
        public void advance(String current, int done, int total) {
            // aucun suivi
        }

        @Override
        public void error(GenerationError error) {
            // aucun suivi
        }
    };

    /** Nouvelle étape, avec son total s'il est connu (0 sinon). */
    void step(Step step, int total);

    /** {@code done} éléments traités sur {@code total} ; {@code current} : page ou ticket en cours. */
    void advance(String current, int done, int total);

    void error(GenerationError error);

    default boolean cancelled() {
        return false;
    }

    /** Levée quand l'interruption demandée est constatée : rien n'est proposé. */
    final class Cancelled extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public Cancelled() {
            super("Génération interrompue", null, false, false);
        }
    }
}
