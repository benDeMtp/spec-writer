package dev.specwriter.application.knowledge;

import dev.specwriter.domain.specification.UseCaseChanges;
import java.time.Instant;
import java.util.List;

/**
 * Rubrique « Sous-tâches de <clé> » d'une version (FR-046, FR-047, data-model.md SubtaskReport),
 * calculée à la demande : rattachement probable de chaque sous-tâche à un cas d'usage, et son état
 * face aux changements de la version.
 */
public record SubtaskReport(String storyKey, String parentKey, Matching matching, String matchingProblem,
        List<Item> subtasks) {

    /** Rattachement par le sens possible, ou non (modèle de recherche absent). */
    public enum Matching { SEMANTIC, UNAVAILABLE }

    /** Proximité, jamais un score brut. */
    public enum Closeness { VERY_CLOSE, CLOSE }

    /** État d'une sous-tâche face à la version. */
    public enum State { TO_REVIEW, DELIVERED_TO_REWORK, CONSISTENT, UNMATCHED }

    /** Cas d'usage rattaché : son code et son titre. */
    public record UseCaseRef(String code, String title) {
    }

    /** Sous-tâche, avec son statut relu dans Jira ({@code statusFresh}) ou indexé et sa date. */
    public record Item(String key, String title, String url, String status, String statusCategory, boolean statusFresh,
            Instant statusDate, UseCaseRef useCase, UseCaseRef alternative, Closeness closeness,
            UseCaseChanges.Change change, State state) {
    }

    public SubtaskReport {
        subtasks = List.copyOf(subtasks);
    }
}
