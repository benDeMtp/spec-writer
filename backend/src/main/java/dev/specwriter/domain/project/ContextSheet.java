package dev.specwriter.domain.project;

/** Les deux fiches du contexte d'un projet (FR-008a, research.md R16 de 002). */
public enum ContextSheet {
    /** Vision et objectifs, acteurs, vocabulaire, règles métier, contraintes fonctionnelles. */
    FUNCTIONAL,
    /** Architecture, contraintes techniques, conventions de code, intégrations. */
    TECHNICAL
}
