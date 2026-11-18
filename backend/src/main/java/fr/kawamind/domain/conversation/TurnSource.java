package fr.kawamind.domain.conversation;

import fr.kawamind.domain.knowledge.Provenance;

/**
 * Élément trouvé pendant un tour et transmis à l'IA sous son numéro {@code n} ; seul ce numéro peut
 * être cité. Sa provenance (016) dit sa source, sa date et son niveau ; {@code null} pour un module
 * ou la spécification discutée. Le texte n'apparaît jamais dans {@link #toString()}.
 */
public record TurnSource(int n, SourceKind kind, String ref, String title, String text, Provenance provenance) {

    public TurnSource(int n, SourceKind kind, String ref, String title, String text) {
        this(n, kind, ref, title, text, null);
    }

    public CitedSource cited() {
        return new CitedSource(n, kind, ref, title, provenance);
    }

    @Override
    public String toString() {
        return "TurnSource[" + n + ", " + kind + ", " + ref + "]";
    }
}
