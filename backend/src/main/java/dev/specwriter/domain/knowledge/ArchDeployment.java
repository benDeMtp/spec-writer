package dev.specwriter.domain.knowledge;

import java.util.Objects;

/** Instance d'un élément sur un nœud de déploiement (data-model.md, ArchDeployment). */
public record ArchDeployment(String element, String environment, String rootNode, String nodePath, int instances,
        boolean sharedInfrastructure) {

    public ArchDeployment {
        Objects.requireNonNull(element);
        Objects.requireNonNull(rootNode);
        instances = Math.max(1, instances);
    }
}
