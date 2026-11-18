package fr.kawamind.domain.knowledge;

/**
 * Niveau de fiabilité d'une source (016, FR-015) : une référence l'emporte sur une interprétation,
 * qui l'emporte sur une source à confirmer.
 */
public enum Reliability {
    REFERENCE("reference", "Référence"),
    INTERPRETATION("interpretation", "Interprétation"),
    TO_CONFIRM("a-confirmer", "À confirmer");

    private final String tag;
    private final String label;

    Reliability(String tag, String label) {
        this.tag = tag;
        this.label = label;
    }

    /** Valeur de l'attribut {@code fiabilite} transmis au modèle. */
    public String tag() {
        return tag;
    }

    public String label() {
        return label;
    }

    /** Vrai si ce niveau est plus fiable que {@code other}. */
    public boolean above(Reliability other) {
        return other == null || ordinal() < other.ordinal();
    }

    /** Le plus fiable des deux ; {@code null} est ignoré. */
    public static Reliability max(Reliability a, Reliability b) {
        if (a == null) {
            return b;
        }
        return a.above(b) || b == null ? a : b;
    }
}
