package dev.specwriter.domain.settings;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

/** Réglages Jira on premise, sans le jeton (data-model.md, JiraSettings ; FR-031, FR-035). */
public record JiraSettings(
        boolean enabled,
        String serverUrl,
        /** Clé Jira par défaut, repli des projets sans clé ; {@code null} sans repli (012). */
        String defaultProjectKey,
        String caCertificatePath,
        String serverVersion,
        LastTest lastTest,
        SecretPersistence secretPersistence,
        boolean insecureHttp) {

    private static final Pattern PROJECT_KEY = Pattern.compile("[A-Z][A-Z0-9_]+");

    public record LastTest(Instant at, JiraTestResult result) {
    }

    /**
     * Valide une saisie. Une adresse {@code http://} n'est acceptée qu'après confirmation de
     * l'avertissement (FR-035).
     */
    public static JiraSettings create(String serverUrl, String projectKey, String caCertificatePath,
            boolean acceptInsecureHttp, SecretPersistence persistence) {
        String url = normalizeUrl(serverUrl);
        boolean insecure = url.startsWith("http://");
        if (insecure && !acceptInsecureHttp) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Cette adresse n'est pas chiffrée (http). Votre jeton circulerait en clair : utilisez https, "
                            + "ou confirmez l'avertissement.");
        }
        // 012 (FR-016) : la clé par défaut est facultative ; elle sert de repli aux projets sans clé
        String key = projectKey == null || projectKey.isBlank() ? null : projectKey.strip();
        if (key != null && !PROJECT_KEY.matcher(key).matches()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "La clé de projet doit être en majuscules, par exemple PROJ ou SPEC_2.");
        }
        String ca = caCertificatePath == null || caCertificatePath.isBlank() ? null : caCertificatePath.strip();
        return new JiraSettings(true, url, key, ca, null, null, persistence, insecure);
    }

    static String normalizeUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalidUrl();
        }
        URI uri;
        try {
            uri = URI.create(raw.strip());
        } catch (IllegalArgumentException e) {
            throw invalidUrl();
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http")) {
            throw invalidUrl();
        }
        if (uri.getHost() == null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || uri.getRawUserInfo() != null) {
            throw invalidUrl();
        }
        String path = uri.getRawPath() == null ? "" : uri.getRawPath().replaceAll("/+$", "");
        return scheme + "://" + uri.getRawAuthority() + path;
    }

    private static DomainException invalidUrl() {
        return new DomainException(ErrorCode.VALIDATION_FAILED,
                "Saisissez l'adresse du serveur Jira, par exemple https://jira.mon-entreprise.fr");
    }

    public JiraSettings withTest(Instant at, JiraTestResult result, String version) {
        return new JiraSettings(enabled, serverUrl, defaultProjectKey, caCertificatePath,
                version != null ? version : serverVersion, new LastTest(at, result), secretPersistence, insecureHttp);
    }

    public JiraSettings withPersistence(SecretPersistence persistence) {
        return new JiraSettings(enabled, serverUrl, defaultProjectKey, caCertificatePath, serverVersion, lastTest,
                persistence, insecureHttp);
    }

    /** Jira Server / Data Center 8.14 ou plus récent (jetons d'accès personnels, research.md R10). */
    public static boolean isSupportedVersion(String version) {
        if (version == null) {
            return false;
        }
        String[] parts = version.split("[.\\-]");
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return major > 8 || (major == 8 && minor >= 14);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
