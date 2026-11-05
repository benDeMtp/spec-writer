package dev.specwriter.domain.knowledge;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Numéro d'un ADR (feature 004, data-model.md), lu dans sa référence puis dans son titre :
 * {@code adr-005.html}, « ADR-12 : … », {@code 0005-titre.html} (convention adr-tools), « 5. Titre ».
 * Les numéros se comparent par valeur : « ADR-5 », « 0005 » et « 5. » sont le même.
 */
public final class AdrNumber {

    private static final Pattern ADR = Pattern.compile("adr[-_ ]?0*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILE = Pattern.compile("^0*(\\d+)[-_]");
    private static final Pattern TITLE = Pattern.compile("^\\s*0*(\\d+)\\.\\s");

    private AdrNumber() {
    }

    public static OptionalInt of(String ref, String title) {
        String file = fileName(ref);
        OptionalInt found = first(ADR, file);
        if (found.isEmpty()) {
            found = first(ADR, title);
        }
        if (found.isEmpty()) {
            found = first(FILE, file);
        }
        return found.isPresent() ? found : first(TITLE, title);
    }

    /** Numéro du successeur cité par un ADR remplacé (« ADR-0005 »). */
    public static OptionalInt ofCited(String supersededBy) {
        return first(ADR, supersededBy);
    }

    private static String fileName(String ref) {
        if (ref == null) {
            return null;
        }
        String path = ref.replaceAll("[?#].*$", "");
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static OptionalInt first(Pattern pattern, String text) {
        if (text == null) {
            return OptionalInt.empty();
        }
        Matcher m = pattern.matcher(text);
        if (!m.find()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(m.group(1)));
        } catch (NumberFormatException e) {
            // numéro hors des entiers : pas un numéro d'ADR utilisable
            return OptionalInt.empty();
        }
    }
}
