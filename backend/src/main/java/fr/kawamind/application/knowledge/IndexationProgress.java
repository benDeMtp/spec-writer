package fr.kawamind.application.knowledge;

/**
 * Suivi d'une indexation (research.md R20) : étape, élément en cours, avancement et erreurs.
 * L'avancement est affiché, jamais journalisé.
 */
public interface IndexationProgress {

    enum Step { MODEL, JIRA, DOCUMENTATION, ARCHITECTURE, EMBEDDING }

    IndexationProgress NONE = new IndexationProgress() {
        @Override
        public void step(Step step, int total) {
            // aucun suivi
        }

        @Override
        public void advance(String current, int done, int total) {
            // aucun suivi
        }

        @Override
        public void error(IndexationError error) {
            // aucun suivi
        }
    };

    void step(Step step, int total);

    void advance(String current, int done, int total);

    void error(IndexationError error);

    /**
     * Bilan d'une source relue (017, FR-008) : clé de la réindexation par source ({@code JIRA},
     * {@code ARCHITECTURE} ou identifiant d'une source de documentation), éléments nouveaux, modifiés
     * et retirés. Transmis à la fin de la synchronisation, avant les vecteurs.
     */
    default void sourceDone(String sourceKey, fr.kawamind.domain.sync.SourceChange change) {
        // aucun suivi par défaut
    }

    /** Source relue en échec, avec le motif de l'erreur, sans contenu (017, FR-012). */
    default void sourceFailed(String sourceKey, String errorCode) {
        // aucun suivi par défaut
    }

    /** Bilan de l'étape Jira, transmis à sa fin, même en erreur (FR-009 de 003). */
    default void tickets(TicketSummary summary) {
        // aucun suivi par défaut
    }

    default boolean cancelled() {
        return false;
    }

    /** Levée quand l'interruption demandée est constatée ; ce qui est déjà indexé est gardé. */
    final class Cancelled extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public Cancelled() {
            super("Indexation interrompue", null, false, false);
        }
    }
}
