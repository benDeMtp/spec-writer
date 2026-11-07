package dev.specwriter.infrastructure.docs;

/**
 * Texte extrait d'un document (006, research.md R1), structuré comme {@link StructuredText} :
 * titres {@code #} à {@code ###}, paragraphes séparés par une ligne vide, tableaux en
 * {@code | a | b |}.
 */
record Extracted(String title, String text) {
}
