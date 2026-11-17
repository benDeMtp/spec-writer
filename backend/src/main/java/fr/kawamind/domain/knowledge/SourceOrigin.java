package fr.kawamind.domain.knowledge;

/**
 * Sorte de source d'un extrait transmis à l'IA (016, research.md R5) : elle dit d'où vient
 * l'extrait. Les six premières ont un niveau de fiabilité réglable ; la fiche générale et les
 * documents joints gardent leurs règles et n'en ont pas.
 */
public enum SourceOrigin {
    JIRA("jira", "Jira"),
    ANTORA("antora", "Antora"),
    DOCUMENTS("sharepoint", "SharePoint"),
    CONFLUENCE("confluence", "Confluence"),
    ARCHITECTURE("architecture", "Modèle d'architecture"),
    SPECIFICATIONS("specification", "Spécifications"),
    SHEET("fiche-generale", "Fiche générale"),
    ATTACHMENT("document-joint", "Document joint");

    private final String tag;
    private final String label;

    SourceOrigin(String tag, String label) {
        this.tag = tag;
        this.label = label;
    }

    /** Valeur de l'attribut {@code source} transmis au modèle (contracts/llm-tags.md). */
    public String tag() {
        return tag;
    }

    public String label() {
        return label;
    }

    /** Vrai si l'utilisateur peut régler le niveau de fiabilité de cette sorte (FR-015). */
    public boolean adjustable() {
        return this != SHEET && this != ATTACHMENT;
    }
}
