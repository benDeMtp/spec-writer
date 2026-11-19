package fr.kawamind.domain.project;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.knowledge.Reliability;
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
 * (data-model.md de 002, FR-013), ou un dossier de documents (Word, Excel, PDF, HTML), par exemple
 * un dossier SharePoint synchronisé (006), avec ses motifs d'exclusion, ou une racine Confluence et
 * toute sa descendance (016), avec son niveau de fiabilité propre ({@code null} : celui de
 * Confluence), l'identifiant et le titre de sa page. Le contenu lu n'est jamais conservé ; seul le
 * résultat de la dernière lecture l'est, sans contenu.
 */
public record DocSource(UUID id, UUID projectId, Kind kind, String location, Instant lastReadAt, ReadSummary lastResult,
        ExclusionPatterns exclusions, Reliability reliability, String pageId, String title) {

    public enum Kind { URL, FOLDER, DOCUMENTS, CONFLUENCE }

    public static final int MAX_TITLE = 500;

    public DocSource {
        exclusions = exclusions == null ? ExclusionPatterns.NONE : exclusions;
        if (kind != Kind.DOCUMENTS && !exclusions.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Les motifs d'exclusion sont réservés au dossier de documents.");
        }
        if (kind != Kind.CONFLUENCE && (reliability != null || pageId != null || title != null)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Le niveau propre, la page et le titre sont réservés aux racines Confluence.");
        }
        if (title != null && title.length() > MAX_TITLE) {
            title = title.substring(0, MAX_TITLE);
        }
    }

    public DocSource(UUID id, UUID projectId, Kind kind, String location, Instant lastReadAt, ReadSummary lastResult,
            ExclusionPatterns exclusions) {
        this(id, projectId, kind, location, lastReadAt, lastResult, exclusions, null, null, null);
    }

    public DocSource(UUID id, UUID projectId, Kind kind, String location, Instant lastReadAt, ReadSummary lastResult) {
        this(id, projectId, kind, location, lastReadAt, lastResult, ExclusionPatterns.NONE);
    }

    /** Résultat de la dernière lecture : nombres de pages, jamais leur contenu. */
    public record ReadSummary(int pagesRead, int pagesInError, boolean limitReached) {
    }

    public static final int MAX_PER_PROJECT = 10;
    public static final int MAX_LOCATION = 2_000;

    public static DocSource create(UUID id, UUID projectId, Kind kind, String location) {
        return create(id, projectId, kind, location, ExclusionPatterns.NONE);
    }

    public static DocSource create(UUID id, UUID projectId, Kind kind, String location, ExclusionPatterns exclusions) {
        return new DocSource(id, projectId, kind, validLocation(kind, location), null, null, exclusions);
    }

    /** Racine Confluence (016) : adresse telle que saisie, page résolue par l'aperçu. */
    public static DocSource confluence(UUID id, UUID projectId, String location, String pageId, String title,
            Reliability reliability) {
        if (pageId == null || pageId.isBlank()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "La page racine Confluence n'a pas été résolue.");
        }
        return new DocSource(id, projectId, Kind.CONFLUENCE, validLocation(Kind.CONFLUENCE, location), null, null,
                ExclusionPatterns.NONE, reliability, pageId, title);
    }

    public DocSource read(Instant at, ReadSummary result) {
        return new DocSource(id, projectId, kind, location, at, result, exclusions, reliability, pageId, title);
    }

    public DocSource withExclusions(ExclusionPatterns patterns) {
        return new DocSource(id, projectId, kind, location, lastReadAt, lastResult, patterns, reliability, pageId, title);
    }

    /** Niveau propre d'une racine Confluence ; {@code null} : celui de Confluence (FR-016a). */
    public DocSource withReliability(Reliability level) {
        if (kind != Kind.CONFLUENCE) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Seule une racine Confluence a un niveau de fiabilité propre.");
        }
        return new DocSource(id, projectId, kind, location, lastReadAt, lastResult, exclusions, level, pageId, title);
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
        return kind == Kind.URL || kind == Kind.CONFLUENCE ? validUrl(value) : validFolder(value);
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
