package dev.specwriter.domain.sources;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.project.ExclusionPatterns;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Racine de sources d'un projet (013, research.md R1) : un dossier du poste, relu à chaque analyse,
 * jamais copié ni indexé, avec ses motifs d'exclusion (mêmes règles que la 006).
 */
public record SourceRoot(UUID id, UUID projectId, String location, ExclusionPatterns exclusions, Instant createdAt) {

    public static final int MAX_PER_PROJECT = 10;
    public static final int MAX_LOCATION = 2_000;

    public SourceRoot {
        exclusions = exclusions == null ? ExclusionPatterns.NONE : exclusions;
    }

    /** Racine déclarée par l'utilisateur ; le refus dit ce qui ne va pas. */
    public static SourceRoot create(UUID id, UUID projectId, String location, ExclusionPatterns exclusions, Instant at) {
        return new SourceRoot(id, projectId, validLocation(location), exclusions, at);
    }

    /** Nom du dossier, affiché dans les analyses. */
    public String label() {
        Path name = Path.of(location).getFileName();
        return name == null ? location : name.toString();
    }

    public SourceRoot withExclusions(ExclusionPatterns patterns) {
        return new SourceRoot(id, projectId, location, patterns, createdAt);
    }

    public static void requireRoomFor(int existing) {
        if (existing >= MAX_PER_PROJECT) {
            throw invalid("Un projet a au plus 10 racines de sources.");
        }
    }

    /**
     * Refuse une racine déjà déclarée, ou imbriquée dans une autre racine du projet (ou qui en
     * contient une) : ses occurrences seraient comptées deux fois.
     */
    public void requireCompatibleWith(List<SourceRoot> others) {
        Path mine = normalized();
        for (SourceRoot other : others) {
            if (other.id().equals(id)) {
                continue;
            }
            Path theirs = other.normalized();
            if (mine.equals(theirs)) {
                throw invalid("Cette racine est déjà déclarée : " + other.label() + ".");
            }
            if (mine.startsWith(theirs) || theirs.startsWith(mine)) {
                throw invalid("Cette racine et la racine " + other.label()
                        + " se contiennent : déclarez l'une ou l'autre.");
            }
        }
    }

    private Path normalized() {
        return Path.of(location).toAbsolutePath().normalize();
    }

    private static String validLocation(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty() || value.length() > MAX_LOCATION) {
            throw invalid("Indiquez un dossier de 2 000 caractères au plus.");
        }
        Path path;
        try {
            path = Path.of(value);
        } catch (InvalidPathException e) {
            throw invalid("Ce chemin n'est pas valide : " + value);
        }
        if (!path.isAbsolute()) {
            throw invalid("Le dossier doit être un chemin absolu (par exemple /Users/moi/rv).");
        }
        if (!Files.exists(path)) {
            throw invalid("Ce dossier n'existe pas : " + value);
        }
        if (!Files.isDirectory(path)) {
            throw invalid("Ce chemin désigne un fichier, pas un dossier : " + value);
        }
        if (!Files.isReadable(path) || !Files.isExecutable(path)) {
            throw invalid("Droits insuffisants pour lire ce dossier : " + value);
        }
        return path.normalize().toString();
    }

    private static DomainException invalid(String message) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, message);
    }
}
