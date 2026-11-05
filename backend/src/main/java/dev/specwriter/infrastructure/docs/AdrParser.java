package dev.specwriter.infrastructure.docs;

import dev.specwriter.domain.knowledge.AdrStatus;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reconnaît une page d'ADR et lit son statut (research.md R20). Une page est un ADR si son titre
 * cite {@code ADR-<n>} ou « Architecture Decision Record », ou si son fichier s'appelle
 * {@code adr-<n>} ; une page d'index sous {@code adr/} n'en est pas un.
 */
final class AdrParser {

    private static final Pattern TITLE = Pattern.compile("\\badr[-_ ]?\\d+\\b|architecture decision record");
    private static final Pattern FILE = Pattern.compile("(^|/)adr[-_]?\\d+[^/]*\\.html?$");
    private static final Pattern STATUS_LINE = Pattern.compile("(?m)^\\s*(?:statut|status)\\s*:\\s*(.+)$");
    private static final Pattern STATUS_TITLE = Pattern.compile("^(?:statut|status)$");

    private AdrParser() {
    }

    static boolean isAdr(String ref, String title) {
        String path = ref == null ? "" : ref.toLowerCase(Locale.ROOT).replaceAll("[?#].*$", "");
        return FILE.matcher(path).find() || title != null && TITLE.matcher(fold(title)).find();
    }

    /**
     * Statut lu dans une ligne « Statut : … » ou « Status: … », sinon dans la première ligne de la
     * section intitulée « Statut » ou « Status ».
     *
     * @param text      texte structuré de la page
     * @param sections  titre et texte de chaque section, dans l'ordre
     */
    static AdrStatus.Statement statement(String text, Map<String, String> sections) {
        Matcher line = STATUS_LINE.matcher(fold(text == null ? "" : text));
        if (line.find()) {
            return AdrStatus.parse(line.group(1));
        }
        for (Map.Entry<String, String> section : sections.entrySet()) {
            if (STATUS_TITLE.matcher(fold(section.getKey()).strip()).matches()) {
                String first = section.getValue().strip().lines().findFirst().orElse("");
                return AdrStatus.parse(first);
            }
        }
        return AdrStatus.Statement.UNKNOWN;
    }

    private static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
