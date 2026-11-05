package dev.specwriter.domain.project;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Source de documentation d'un projet : un site Antora par son lien ou par son dossier local
 * (data-model.md de 002, FR-013). Le contenu lu n'est jamais conservé ; seul le résultat de la
 * dernière lecture l'est, sans contenu.
 */
public record DocSource(UUID id, UUID projectId, Kind kind, String location, Instant lastReadAt, ReadSummary lastResult) {

    public enum Kind { URL, FOLDER }

    /** Résultat de la dernière lecture : nombres de pages, jamais leur contenu. */
    public record ReadSummary(int pagesRead, int pagesInError, boolean limitReached) {
    }

    public static final int MAX_PER_PROJECT = 10;
    public static final int MAX_LOCATION = 2_000;

    public static DocSource create(UUID id, UUID projectId, Kind kind, String location) {
        return new DocSource(id, projectId, kind, validLocation(kind, location), null, null);
    }

    public DocSource read(Instant at, ReadSummary result) {
        return new DocSource(id, projectId, kind, location, at, result);
    }

    /** Refuse une source de plus quand le projet en a déjà {@link #MAX_PER_PROJECT}. */
    public static void requireRoomFor(int existing) {
        if (existing >= MAX_PER_PROJECT) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Un projet a au plus 10 sources de documentation.");
        }
    }

    private static String validLocation(Kind kind, String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty() || value.length() > MAX_LOCATION) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Indiquez un lien ou un dossier de 2 000 caractères au plus.");
        }
        return kind == Kind.URL ? validUrl(value) : validFolder(value);
    }

    private static String validUrl(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!uri.isAbsolute() || !scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null
                    || uri.getRawUserInfo() != null) {
                throw invalidUrl();
            }
            return value;
        } catch (URISyntaxException e) {
            throw invalidUrl();
        }
    }

    private static DomainException invalidUrl() {
        return new DomainException(ErrorCode.VALIDATION_FAILED,
                "Le lien doit être une adresse http ou https complète, sans identifiant ni mot de passe.");
    }

    /** Le refus dit ce qui ne va pas : chemin relatif, absent, fichier ou illisible. */
    private static String validFolder(String value) {
        Path path;
        try {
            path = Path.of(value);
        } catch (InvalidPathException e) {
            throw invalidFolder("Ce chemin n'est pas valide : " + value);
        }
        if (!path.isAbsolute()) {
            throw invalidFolder("Le dossier doit être un chemin absolu (par exemple /Users/moi/site).");
        }
        if (!Files.exists(path)) {
            throw invalidFolder("Ce dossier n'existe pas : " + value);
        }
        if (!Files.isDirectory(path)) {
            throw invalidFolder("Ce chemin désigne un fichier, pas un dossier : " + value);
        }
        if (!Files.isReadable(path) || !Files.isExecutable(path)) {
            throw invalidFolder("Droits insuffisants pour lire ce dossier : " + value);
        }
        return value;
    }

    private static DomainException invalidFolder(String message) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, message);
    }
}
