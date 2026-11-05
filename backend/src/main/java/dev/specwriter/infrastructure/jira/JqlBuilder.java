package dev.specwriter.infrastructure.jira;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * Requêtes JQL construites à partir de la saisie (OWASP A03). Les caractères spéciaux de Lucene
 * sont retirés et les guillemets échappés : la saisie reste un seul littéral.
 */
final class JqlBuilder {

    private static final Pattern PROJECT = Pattern.compile("[A-Z][A-Z0-9_]+");
    private static final Pattern LUCENE = Pattern.compile("[+\\-&|!(){}\\[\\]^~*?\\\\:]");

    private static final DateTimeFormatter JQL_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
            .withZone(ZoneOffset.UTC);
    static final Duration SINCE_MARGIN = Duration.ofDays(1);

    private JqlBuilder() {
    }

    /** Tous les tickets du projet, du plus récemment modifié au plus ancien. */
    static String recent(String projectKey) {
        requireProject(projectKey);
        return "project = " + projectKey + " ORDER BY updated DESC";
    }

    /**
     * Tickets modifiés depuis {@code since}, du plus ancien au plus récent (research.md R20). Jira
     * lit la date dans le fuseau de l'utilisateur : une marge d'un jour évite d'en manquer.
     */
    static String updatedSince(String projectKey, Instant since) {
        requireProject(projectKey);
        if (since == null) {
            return "project = " + projectKey + " ORDER BY updated ASC";
        }
        return "project = " + projectKey + " AND updated >= \"" + JQL_DATE.format(since.minus(SINCE_MARGIN))
                + "\" ORDER BY updated ASC";
    }

    /** Tous les tickets du projet, par clé, pour en lister les clés. */
    static String allKeys(String projectKey) {
        requireProject(projectKey);
        return "project = " + projectKey + " ORDER BY key ASC";
    }

    static String textSearch(String projectKey, String terms) {
        requireProject(projectKey);
        String cleaned = LUCENE.matcher(terms == null ? "" : terms).replaceAll(" ").replaceAll("\\s+", " ").trim();
        if (cleaned.replace("\"", "").isBlank()) {
            throw new IllegalArgumentException("Recherche vide");
        }
        return "project = " + projectKey + " AND text ~ \"" + cleaned.replace("\"", "\\\"") + "\" ORDER BY updated DESC";
    }

    private static void requireProject(String projectKey) {
        if (projectKey == null || !PROJECT.matcher(projectKey).matches()) {
            throw new IllegalArgumentException("Clé de projet invalide");
        }
    }
}
