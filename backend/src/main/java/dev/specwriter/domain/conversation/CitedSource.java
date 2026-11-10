package dev.specwriter.domain.conversation;

/** Source citée par une réponse, sous le numéro qu'elle porte dans le texte (010, FR-003). */
public record CitedSource(int n, SourceKind kind, String ref, String title) {
}
