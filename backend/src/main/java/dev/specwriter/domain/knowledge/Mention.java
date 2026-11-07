package dev.specwriter.domain.knowledge;

import java.util.List;

/**
 * Mention @ trouvée dans un texte de l'utilisateur (007, data-model.md) : son texte tel qu'écrit,
 * sa position, et les documents qui portent ce nom (aucun : introuvable ; plusieurs : ambiguë).
 */
public record Mention(String text, MentionField field, int start, int end, List<Mentionable> matches) {

    public Mention {
        matches = matches == null ? List.of() : List.copyOf(matches);
    }

    public boolean resolved() {
        return matches.size() == 1;
    }

    public boolean ambiguous() {
        return matches.size() > 1;
    }

    public boolean notFound() {
        return matches.isEmpty();
    }
}
