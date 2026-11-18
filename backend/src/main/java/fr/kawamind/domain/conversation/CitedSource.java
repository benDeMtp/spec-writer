package fr.kawamind.domain.conversation;

import fr.kawamind.domain.knowledge.Provenance;

/**
 * Source citée par une réponse, sous le numéro qu'elle porte dans le texte (010, FR-003), avec sa
 * provenance (016) ; {@code null} pour une réponse antérieure à 016.
 */
public record CitedSource(int n, SourceKind kind, String ref, String title, Provenance provenance) {

    public CitedSource(int n, SourceKind kind, String ref, String title) {
        this(n, kind, ref, title, null);
    }
}
