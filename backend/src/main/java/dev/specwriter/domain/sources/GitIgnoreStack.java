package dev.specwriter.domain.sources;

import java.util.ArrayList;
import java.util.List;

/**
 * Règles {@code .gitignore} des dossiers parents du dossier parcouru (013, research.md R3) : le
 * fichier le plus profond l'emporte. Le parcours ne descend pas dans un dossier ignoré, si bien
 * qu'une négation ne peut pas en sortir un fichier, comme dans git.
 */
public final class GitIgnoreStack {

    public static final GitIgnoreStack EMPTY = new GitIgnoreStack(List.of());

    /** Du plus profond au moins profond. */
    private final List<GitIgnoreRules> levels;

    private GitIgnoreStack(List<GitIgnoreRules> levels) {
        this.levels = levels;
    }

    /** Pile complétée des règles d'un dossier plus profond ; inchangée si elles sont vides. */
    public GitIgnoreStack push(GitIgnoreRules rules) {
        if (rules.isEmpty()) {
            return this;
        }
        List<GitIgnoreRules> next = new ArrayList<>(levels.size() + 1);
        next.add(rules);
        next.addAll(levels);
        return new GitIgnoreStack(List.copyOf(next));
    }

    public boolean ignored(String relativePath, boolean directory) {
        for (GitIgnoreRules rules : levels) {
            GitIgnoreRules.Match match = rules.match(relativePath, directory);
            if (match != GitIgnoreRules.Match.NONE) {
                return match == GitIgnoreRules.Match.IGNORED;
            }
        }
        return false;
    }
}
