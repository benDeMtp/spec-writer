package dev.specwriter.infrastructure.jira;

import dev.specwriter.domain.port.IssueTracker;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Requêtes JQL construites à partir de la saisie (OWASP A03). Les caractères spéciaux de Lucene
 * sont retirés et les guillemets échappés : la saisie reste un seul littéral.
 */
final class JqlBuilder {

    private static final Pattern PROJECT = Pattern.compile("[A-Z][A-Z0-9_]+");
    private static final Pattern ISSUE_KEY = Pattern.compile("[A-Z][A-Z0-9_]+-\\d+");
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

    /**
     * Tickets ouverts ou modifiés depuis {@code since} (non nul), du plus ancien au plus récent :
     * l'indexation complète garde les tickets fermés sans les relire (research.md R1 de 003).
     */
    static String openOrUpdatedSince(String projectKey, Instant since) {
        requireProject(projectKey);
        Objects.requireNonNull(since, "since");
        return "project = " + projectKey + " AND (statusCategory != Done OR updated >= \""
                + JQL_DATE.format(since.minus(SINCE_MARGIN)) + "\") ORDER BY updated ASC";
    }

    /** Tickets du projet par clé, de 1 à {@link IssueTracker#KEYS_PER_READ} clés validées. */
    static String keys(String projectKey, List<String> keys) {
        requireProject(projectKey);
        if (keys == null || keys.isEmpty() || keys.size() > IssueTracker.KEYS_PER_READ) {
            throw new IllegalArgumentException("De 1 à " + IssueTracker.KEYS_PER_READ + " clés");
        }
        for (String key : keys) {
            if (key == null || !ISSUE_KEY.matcher(key).matches()) {
                throw new IllegalArgumentException("Clé de ticket invalide");
            }
        }
        return "project = " + projectKey + " AND key in (" + String.join(", ", keys) + ") ORDER BY key ASC";
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
