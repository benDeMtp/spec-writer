package dev.specwriter.domain.knowledge;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Sources de connaissance d'un projet propres à l'index (data-model.md, Project) : workspace
 * Structurizr et identifiant d'ownership, empreinte du modèle lu, repère de synchronisation Jira
 * et date de la dernière indexation.
 */
public record KnowledgeSource(UUID projectId, String workspace, String ownership, String architectureHash,
        Instant jiraMark, Instant syncedAt) {

    public static final int MAX_PATH = 2_000;
    public static final int MAX_OWNERSHIP = 80;

    public KnowledgeSource {
        Objects.requireNonNull(projectId);
        workspace = blankToNull(workspace);
        ownership = blankToNull(ownership);
    }

    public static KnowledgeSource empty(UUID projectId) {
        return new KnowledgeSource(projectId, null, null, null, null, null);
    }

    public KnowledgeSource withArchitecture(String newWorkspace, String newOwnership) {
        return new KnowledgeSource(projectId, newWorkspace, newOwnership, null, jiraMark, syncedAt);
    }

    public KnowledgeSource withArchitectureHash(String hash) {
        return new KnowledgeSource(projectId, workspace, ownership, hash, jiraMark, syncedAt);
    }

    public KnowledgeSource synced(Instant mark, Instant at) {
        return new KnowledgeSource(projectId, workspace, ownership, architectureHash, mark, at);
    }

    /**
     * Chemin d'un fichier {@code workspace.dsl} : absolu, existant, un fichier, lisible ; le refus
     * dit ce qui ne va pas (FR-038).
     */
    public static String validWorkspace(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("Indiquez le chemin du fichier workspace.dsl.");
        }
        String trimmed = value.strip();
        if (trimmed.length() > MAX_PATH) {
            throw invalid("Le chemin dépasse 2 000 caractères.");
        }
        Path path;
        try {
            path = Path.of(trimmed);
        } catch (InvalidPathException e) {
            throw invalid("Ce chemin n'est pas valide : " + trimmed);
        }
        if (!path.isAbsolute()) {
            throw invalid("Le workspace doit être un chemin absolu (par exemple /Users/moi/C4/projet/workspace.dsl).");
        }
        if (!Files.exists(path)) {
            throw invalid("Ce fichier n'existe pas : " + trimmed);
        }
        if (Files.isDirectory(path)) {
            throw invalid("Ce chemin désigne un dossier : indiquez le fichier workspace.dsl qu'il contient.");
        }
        if (!Files.isReadable(path)) {
            throw invalid("Droits insuffisants pour lire ce fichier : " + trimmed);
        }
        return trimmed;
    }

    public static String validOwnership(String value) {
        if (value != null && value.strip().length() > MAX_OWNERSHIP) {
            throw invalid("L'identifiant d'ownership dépasse 80 caractères.");
        }
        return value;
    }

    private static DomainException invalid(String message) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, message);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
