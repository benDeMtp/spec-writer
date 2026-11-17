package fr.kawamind.domain.settings;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;

/**
 * Serveur Confluence Server / Data Center de l'outil (016, R4), sans le jeton, qui est au trousseau.
 * Mêmes règles que Jira : adresse normalisée, {@code http://} seulement après confirmation,
 * certificat d'autorité interne lisible.
 */
public record ConfluenceSettings(String serverUrl, String caCertificatePath, String serverVersion, LastTest lastTest,
        SecretPersistence secretPersistence, boolean insecureHttp) {

    public static final int MAX_URL = 2_000;

    public record LastTest(Instant at, ConfluenceTestResult result) {
    }

    public static ConfluenceSettings create(String serverUrl, String caCertificatePath, boolean acceptInsecureHttp,
            SecretPersistence persistence) {
        String url = normalizeUrl(serverUrl);
        boolean insecure = url.startsWith("http://");
        if (insecure && !acceptInsecureHttp) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Cette adresse n'est pas chiffrée (http). Votre jeton circulerait en clair : utilisez https, "
                            + "ou confirmez l'avertissement.");
        }
        String ca = caCertificatePath == null || caCertificatePath.isBlank() ? null : readable(caCertificatePath.strip());
        return new ConfluenceSettings(url, ca, null, null, persistence, insecure);
    }

    static String normalizeUrl(String raw) {
        if (raw == null || raw.isBlank() || raw.strip().length() > MAX_URL) {
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

    private static String readable(String path) {
        try {
            Path pem = Path.of(path);
            if (!pem.isAbsolute() || !Files.isRegularFile(pem) || !Files.isReadable(pem)) {
                throw unreadable(path);
            }
            return path;
        } catch (InvalidPathException e) {
            throw unreadable(path);
        }
    }

    private static DomainException unreadable(String path) {
        return new DomainException(ErrorCode.VALIDATION_FAILED,
                "Le certificat est introuvable ou illisible : " + path + ". Indiquez le chemin complet d'un fichier"
                        + " PEM.");
    }

    private static DomainException invalidUrl() {
        return new DomainException(ErrorCode.VALIDATION_FAILED,
                "Saisissez l'adresse du serveur Confluence, par exemple https://wiki.mon-entreprise.fr");
    }

    public ConfluenceSettings withTest(Instant at, ConfluenceTestResult result, String version) {
        return new ConfluenceSettings(serverUrl, caCertificatePath, version != null ? version : serverVersion,
                new LastTest(at, result), secretPersistence, insecureHttp);
    }

    public ConfluenceSettings withPersistence(SecretPersistence persistence) {
        return new ConfluenceSettings(serverUrl, caCertificatePath, serverVersion, lastTest, persistence, insecureHttp);
    }
}
