package dev.specwriter.domain.settings;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Modèle d'embedding remplacé par un dossier local (research.md R19) : dossier contenant
 * {@code model.onnx} et {@code tokenizer.json}, pooling et préfixes. Sans dossier, le modèle par
 * défaut (multilingual-e5-small) et ses préfixes s'appliquent.
 */
public record EmbeddingSettings(String folder, Pooling pooling, String queryPrefix, String passagePrefix) {

    public static final int MAX_FOLDER = 2_000;
    public static final int MAX_PREFIX = 50;
    public static final EmbeddingSettings DEFAULT = new EmbeddingSettings(null, Pooling.MEAN, null, null);

    public enum Pooling {
        MEAN,
        CLS
    }

    public EmbeddingSettings {
        folder = folder == null || folder.isBlank() ? null : folder.strip();
        if (folder != null && folder.length() > MAX_FOLDER) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Le dossier du modèle dépasse 2 000 caractères.");
        }
        pooling = pooling == null ? Pooling.MEAN : pooling;
        if (queryPrefix != null && queryPrefix.length() > MAX_PREFIX
                || passagePrefix != null && passagePrefix.length() > MAX_PREFIX) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Un préfixe dépasse 50 caractères.");
        }
    }

    /**
     * Dossier de remplacement saisi : chemin absolu d'un dossier qui contient {@code model.onnx} et
     * {@code tokenizer.json}, ou un seul fichier de chaque suffixe ; {@code null} pour revenir au modèle par défaut.
     */
    public static String validFolder(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.length() > MAX_FOLDER) {
            throw invalid("Le dossier du modèle dépasse 2 000 caractères.");
        }
        Path path;
        try {
            path = Path.of(trimmed);
        } catch (InvalidPathException e) {
            throw invalid("Ce chemin n'est pas valide : " + trimmed);
        }
        if (!path.isAbsolute()) {
            throw invalid("Le dossier du modèle doit être un chemin absolu (par exemple /Users/moi/modeles/e5).");
        }
        if (!Files.isDirectory(path)) {
            throw invalid("Ce dossier n'existe pas : " + trimmed);
        }
        requireFile(path, "model.onnx", ".onnx");
        requireFile(path, "tokenizer.json", "tokenizer.json");
        return trimmed;
    }

    /** Le fichier attendu, ou un seul fichier de ce suffixe (règle du chargement du modèle). */
    private static void requireFile(Path folder, String preferred, String suffix) {
        if (Files.isRegularFile(folder.resolve(preferred))) {
            return;
        }
        long count;
        try (Stream<Path> files = Files.list(folder)) {
            count = files.filter(f -> f.getFileName() != null && f.getFileName().toString().endsWith(suffix)).count();
        } catch (IOException e) {
            throw invalid("Droits insuffisants pour lire ce dossier : " + folder);
        }
        if (count != 1) {
            throw invalid("Le dossier doit contenir " + preferred + ", ou un seul fichier " + suffix + " : " + folder);
        }
    }

    public static Pooling pooling(String value) {
        if (value == null || value.isBlank()) {
            return Pooling.MEAN;
        }
        try {
            return Pooling.valueOf(value.strip());
        } catch (IllegalArgumentException e) {
            throw invalid("Choisissez le pooling MEAN ou CLS.");
        }
    }

    private static DomainException invalid(String message) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, message);
    }

    public boolean usesDefaultModel() {
        return folder == null;
    }
}
