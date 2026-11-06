package dev.specwriter.application.knowledge;

/**
 * Source proposée au choix d'une réindexation (005, research.md R5).
 *
 * @param id    {@code JIRA}, {@code ARCHITECTURE} ou l'identifiant de la source de documentation
 * @param label « Jira CLE », l'emplacement de la documentation, ou le workspace
 */
public record IndexableSource(String id, Kind kind, String label) {

    public enum Kind { JIRA, DOCUMENTATION, ARCHITECTURE }
}
