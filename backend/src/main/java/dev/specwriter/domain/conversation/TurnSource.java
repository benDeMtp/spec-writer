package dev.specwriter.domain.conversation;

/**
 * Élément trouvé pendant un tour et transmis à l'IA sous son numéro {@code n} ; seul ce numéro peut
 * être cité. Le texte n'apparaît jamais dans {@link #toString()}.
 */
public record TurnSource(int n, SourceKind kind, String ref, String title, String text) {

    public CitedSource cited() {
        return new CitedSource(n, kind, ref, title);
    }

    @Override
    public String toString() {
        return "TurnSource[" + n + ", " + kind + ", " + ref + "]";
    }
}
