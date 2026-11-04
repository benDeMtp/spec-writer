package dev.specwriter.domain.settings;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;

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

    public boolean usesDefaultModel() {
        return folder == null;
    }
}
