package dev.specwriter.domain.knowledge;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Statut d'un ADR, lu dans sa page (ligne « Statut » ou « Status », research.md R20). */
public enum AdrStatus {
    ACCEPTED,
    PROPOSED,
    SUPERSEDED,
    DEPRECATED,
    UNKNOWN;

    /** Successeur cité « ADR-5 », ou par son seul numéro (« 0005 », « [5. Titre](…) », convention adr-tools). */
    private static final Pattern REPLACED_BY = Pattern.compile(
            "(?:remplace par|superseded by)\\s+(?:adr-?\\s*|\\[)?(\\d+)");

    /** Statut et, pour un ADR remplacé, la référence de celui qui le remplace. */
    public record Statement(AdrStatus status, String supersededBy) {
        public static final Statement UNKNOWN = new Statement(AdrStatus.UNKNOWN, null);

        public static Statement of(AdrStatus status, String supersededBy) {
            return new Statement(status, status == SUPERSEDED ? supersededBy : null);
        }
    }

    public static Statement parse(String line) {
        if (line == null) {
            return Statement.UNKNOWN;
        }
        String text = Normalizer.normalize(line, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        Matcher replaced = REPLACED_BY.matcher(text);
        if (replaced.find()) {
            return Statement.of(SUPERSEDED, "ADR-" + replaced.group(1));
        }
        if (text.contains("remplace") || text.contains("superseded")) {
            return Statement.of(SUPERSEDED, null);
        }
        if (text.contains("accepte")) {
            return Statement.of(ACCEPTED, null);
        }
        if (text.contains("propose")) {
            return Statement.of(PROPOSED, null);
        }
        if (text.contains("obsolete") || text.contains("deprecated")) {
            return Statement.of(DEPRECATED, null);
        }
        return Statement.UNKNOWN;
    }
}
