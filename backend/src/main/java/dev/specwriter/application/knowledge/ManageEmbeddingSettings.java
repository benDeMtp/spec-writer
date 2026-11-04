package dev.specwriter.application.knowledge;

import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.domain.port.SettingsRepository;
import dev.specwriter.domain.settings.EmbeddingSettings;

/**
 * Carte « Modèle de recherche » (research.md R19) : état du modèle, et son remplacement par un
 * dossier local (pooling et préfixes). Un dossier est chargé aussitôt pour montrer s'il convient ;
 * le modèle par défaut, lui, est téléchargé à la prochaine indexation. Un autre modèle impose la
 * revectorisation, faite par l'indexation suivante.
 */
public class ManageEmbeddingSettings {

    /** État et réglages du modèle. */
    public record View(EmbeddingPort.State state, String modelId, String problem, boolean defaultModel, String folder,
            EmbeddingSettings.Pooling pooling, String queryPrefix, String passagePrefix) {
    }

    private static final EmbeddingPort.Progress SILENT = new EmbeddingPort.Progress() {
        @Override
        public void advanced(long done, long total) {
            // le chargement d'un dossier local n'a pas d'avancement à montrer
        }

        @Override
        public boolean cancelled() {
            return false;
        }
    };

    private final SettingsRepository settings;
    private final EmbeddingPort embedding;

    public ManageEmbeddingSettings(SettingsRepository settings, EmbeddingPort embedding) {
        this.settings = settings;
        this.embedding = embedding;
    }

    public View get() {
        EmbeddingSettings s = settings.embedding();
        return new View(embedding.state(), embedding.modelId(), embedding.problem(), s.usesDefaultModel(), s.folder(),
                s.pooling(), s.queryPrefix(), s.passagePrefix());
    }

    public View save(String folder, String pooling, String queryPrefix, String passagePrefix) {
        String valid = EmbeddingSettings.validFolder(folder);
        EmbeddingSettings s = valid == null ? EmbeddingSettings.DEFAULT
                : new EmbeddingSettings(valid, EmbeddingSettings.pooling(pooling), blankToNull(queryPrefix),
                        blankToNull(passagePrefix));
        settings.saveEmbedding(s);
        if (!s.usesDefaultModel()) {
            embedding.prepare(SILENT);
        }
        return get();
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
