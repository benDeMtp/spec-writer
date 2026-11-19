package fr.kawamind.domain.jira;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Transition offerte par le workflow Jira d'un ticket ({@code issue/{key}/transitions}) : son
 * identifiant, son nom, la catégorie du statut d'arrivée ({@code new}, {@code indeterminate},
 * {@code done}), sur laquelle spec-writer choisit (015, research R3), et le nom de ce statut.
 */
public record Transition(String id, String name, String toCategory, String toStatus) {

    /**
     * Une annulation mène aussi à la catégorie « terminé » (Annulé, Won't Do, Doublon…) : reconnue au
     * nom de la transition ou du statut d'arrivée, en français comme en anglais.
     */
    private static final Pattern CANCELLATION = Pattern.compile(
            "annul|cancel|abandon|rejet|reject|refus|won.?t|wont|invalid|doublon|duplicate|obsolete|sans suite|declin");
    /** Statut ou transition qui dit qu'un travail est fini. */
    private static final Pattern COMPLETION = Pattern.compile(
            "termin|done|fini|fait|resolu|resolve|ferme|close|clos|livre|deliver|complet|valide");

    public Transition(String id, String name, String toCategory) {
        this(id, name, toCategory, null);
    }

    public boolean cancellation() {
        return CANCELLATION.matcher(words()).find();
    }

    public boolean completion() {
        return COMPLETION.matcher(words()).find();
    }

    /** Nom et statut d'arrivée, en minuscules et sans accents. */
    private String words() {
        String text = (name == null ? "" : name) + " " + (toStatus == null ? "" : toStatus);
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
