package fr.kawamind.domain.task;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Nom d'un statut Jira (023, research R3) : désigné tel que Jira l'affiche, comparé sans la casse,
 * les accents ni les espaces autour.
 */
public final class StatusName {

    private StatusName() {
    }

    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        return Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).strip();
    }

    public static boolean same(String a, String b) {
        return normalize(a).equals(normalize(b));
    }
}
