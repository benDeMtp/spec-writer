package dev.specwriter.domain.port;

/** Conversion entre le Markdown de spec-writer et le wiki markup de Jira (research.md R10). */
public interface WikiMarkup {

    String toWiki(String markdown);

    /** Pour l'affichage et l'import : les blocs non reconnus restent lisibles, en bloc de code. */
    String toMarkdown(String wiki);
}
