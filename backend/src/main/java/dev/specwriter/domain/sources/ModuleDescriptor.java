package dev.specwriter.domain.sources;

import java.util.List;
import java.util.Set;

/**
 * Ce qu'un {@code pom.xml} dit d'un module (013, research.md R5) : nom ({@code artifactId}), coordonnées
 * {@code groupId:artifactId}, parent, dépendances, imports de BOM et modules agrégés. Les versions
 * ne comptent pas ; une valeur {@code ${…}} est écartée et comptée dans {@code unresolved}.
 */
public record ModuleDescriptor(String artifactId, String coordinates, String parent, Set<String> dependencies, Set<String> bomImports,
        List<String> aggregated, int unresolved) {

    public ModuleDescriptor {
        dependencies = Set.copyOf(dependencies);
        bomImports = Set.copyOf(bomImports);
        aggregated = List.copyOf(aggregated);
    }

    /** Descripteur d'un pom illisible : aucune relation. */
    public static ModuleDescriptor unreadable() {
        return new ModuleDescriptor(null, null, null, Set.of(), Set.of(), List.of(), 0);
    }
}
