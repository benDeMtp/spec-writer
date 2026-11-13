package dev.specwriter.domain.sources;

/** Sorte d'un module, d'après son descripteur (013, research.md R4). */
public enum ModuleKind {
    /** {@code pom.xml}. */
    MAVEN,
    /** {@code package.json}, hors {@code node_modules}. */
    NPM,
    /** {@code Chart.yaml}. */
    HELM,
    /** Fichiers d'une racine hors de tout module. */
    ROOT
}
