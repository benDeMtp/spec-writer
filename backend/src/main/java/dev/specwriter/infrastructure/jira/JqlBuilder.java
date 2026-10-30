package dev.specwriter.infrastructure.jira;

import java.util.regex.Pattern;

/**
 * Requêtes JQL construites à partir de la saisie (OWASP A03). Les caractères spéciaux de Lucene
 * sont retirés et les guillemets échappés : la saisie reste un seul littéral.
 */
final class JqlBuilder {

    private static final Pattern PROJECT = Pattern.compile("[A-Z][A-Z0-9_]+");
    private static final Pattern LUCENE = Pattern.compile("[+\\-&|!(){}\\[\\]^~*?\\\\:]");

    private JqlBuilder() {
    }

    static String textSearch(String projectKey, String terms) {
        if (projectKey == null || !PROJECT.matcher(projectKey).matches()) {
            throw new IllegalArgumentException("Clé de projet invalide");
        }
        String cleaned = LUCENE.matcher(terms == null ? "" : terms).replaceAll(" ").replaceAll("\\s+", " ").trim();
        if (cleaned.replace("\"", "").isBlank()) {
            throw new IllegalArgumentException("Recherche vide");
        }
        return "project = " + projectKey + " AND text ~ \"" + cleaned.replace("\"", "\\\"") + "\"";
    }
}
