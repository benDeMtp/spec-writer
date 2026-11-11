package dev.specwriter.domain.sources;

import java.util.Locale;

/**
 * Occurrence d'un terme (013, FR-011) : fichier relatif à la racine, ligne à partir de 1, et extrait
 * borné de la ligne, centré sur le terme. Seul contenu des sources conservé (FR-007).
 */
public record Occurrence(String path, int line, String term, String excerpt) {

    public static final int MAX_EXCERPT = 200;

    public static Occurrence of(String path, int line, String term, String rawLine) {
        String clean = clean(rawLine);
        int at = clean.toLowerCase(Locale.ROOT).indexOf(term.toLowerCase(Locale.ROOT));
        if (clean.length() > MAX_EXCERPT) {
            int start = at < 0 ? 0 : Math.max(0, at + term.length() / 2 - MAX_EXCERPT / 2);
            start = Math.min(start, clean.length() - MAX_EXCERPT);
            clean = clean.substring(start, start + MAX_EXCERPT);
        }
        return new Occurrence(path, line, term, clean.strip());
    }

    private static String clean(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            out.append(c == '\t' ? ' ' : Character.isISOControl(c) ? ' ' : c);
        }
        return out.toString().strip();
    }
}
