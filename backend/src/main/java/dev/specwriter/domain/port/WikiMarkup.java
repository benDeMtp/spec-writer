package dev.specwriter.domain.port;

/** Conversion entre le Markdown de spec-writer et le wiki markup de Jira (research.md R10). */
public interface WikiMarkup {

    String toWiki(String markdown);

    /**
     * Pour l'affichage, l'import et la fusion (FR-043a) : les blocs non reconnus restent lisibles, en
     * bloc de code. Images, pièces jointes et mentions renvoient vers {@code issueUrl} (peut être nul).
     */
    String toMarkdown(String wiki, String issueUrl);

    /**
     * Texte riche produit par le LLM (FR-012b) : ses tableaux écrits en wiki markup deviennent des
     * tableaux Markdown ; le reste du texte, Markdown, est inchangé.
     */
    String wikiTablesToMarkdown(String markdown);
}
