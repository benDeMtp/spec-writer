package dev.specwriter.domain.sources;

import java.util.UUID;

/**
 * Identité d'un module : sa racine et son chemin relatif, écrite {@code <rootId>/<chemin>} (013,
 * data-model.md). Le chemin est vide pour le pseudo-module de la racine.
 */
public record ModuleId(UUID rootId, String path) implements Comparable<ModuleId> {

    public static ModuleId parse(String text) {
        int slash = text.indexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("Identifiant de module sans « / »");
        }
        return new ModuleId(UUID.fromString(text.substring(0, slash)), text.substring(slash + 1));
    }

    @Override
    public int compareTo(ModuleId other) {
        int byRoot = rootId.compareTo(other.rootId);
        return byRoot != 0 ? byRoot : path.compareTo(other.path);
    }

    @Override
    public String toString() {
        return rootId + "/" + path;
    }
}
