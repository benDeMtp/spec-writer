package dev.specwriter.domain.sources;

/** Réponse de l'agent pour un module, désigné par son identifiant texte {@code <rootId>/<chemin>}. */
public record ModuleQualification(String id, Qualification qualification, String justification) {
}
