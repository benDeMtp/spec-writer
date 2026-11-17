package fr.kawamind.domain.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Places réservées aux extraits de référence (016, R7, FR-019, FR-020) : la coupe d'un résultat
 * classé garde au moins deux extraits de référence (ou tous s'il y en a moins parmi les candidats).
 * Les mieux classés des candidats de référence absents remplacent les derniers extraits qui ne sont
 * pas de référence, deux au plus ; l'ordre relatif des autres est gardé et aucun score n'est modifié.
 */
public final class ReservedPlaces {

    public static final int MIN_REFERENCES = 2;

    private ReservedPlaces() {
    }

    /**
     * {@code candidates} : tous les candidats examinés, dans l'ordre du classement ; le résultat
     * normal est leurs {@code max} premiers.
     */
    public static <T> List<T> apply(List<T> candidates, int max, Predicate<T> reference) {
        List<T> ranked = new ArrayList<>(new LinkedHashSet<>(candidates));
        List<T> cut = new ArrayList<>(ranked.subList(0, Math.min(max, ranked.size())));
        long present = cut.stream().filter(reference).count();
        if (present >= MIN_REFERENCES) {
            return cut;
        }
        Set<T> kept = new LinkedHashSet<>(cut);
        List<T> missing = ranked.stream().filter(reference).filter(c -> !kept.contains(c))
                .limit(MIN_REFERENCES - present).toList();
        for (T added : missing) {
            int last = lastNonReference(cut, reference);
            if (last < 0) {
                if (cut.size() >= max) {
                    break;
                }
            } else {
                cut.remove(last);
            }
            cut.add(added);
        }
        return cut;
    }

    private static <T> int lastNonReference(List<T> cut, Predicate<T> reference) {
        for (int i = cut.size() - 1; i >= 0; i--) {
            if (!reference.test(cut.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
