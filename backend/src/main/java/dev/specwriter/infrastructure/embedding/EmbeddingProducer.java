package dev.specwriter.infrastructure.embedding;

import dev.langchain4j.model.embedding.onnx.PoolingMode;
import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.domain.port.SettingsRepository;
import dev.specwriter.domain.settings.EmbeddingSettings;
import dev.specwriter.infrastructure.platform.ProxiedHttp;
import dev.specwriter.infrastructure.platform.ProxySettings;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Port d'embedding (research.md R19) : modèle par défaut téléchargé par le proxy d'entreprise, ou
 * dossier local des réglages. {@code specwriter.embedding.folder} impose un dossier (modèle de test
 * en dev mode, en test et en E2E).
 */
public class EmbeddingProducer {

    @ConfigProperty(name = "specwriter.data-dir.resolved")
    String dataDir;

    @ConfigProperty(name = "specwriter.embedding.base-url", defaultValue = "https://huggingface.co")
    URI baseUrl;

    @ConfigProperty(name = "specwriter.embedding.folder")
    Optional<String> forcedFolder;

    @Produces
    @Singleton
    EmbeddingPort embedding(ProxySettings proxy, SettingsRepository settings) {
        ModelDownloader downloader = new ModelDownloader(
                ProxiedHttp.builder(proxy).connectTimeout(Duration.ofSeconds(10)).build(),
                Path.of(dataDir, "models"));
        return new OnnxEmbeddingAdapter(downloader, OnnxEmbeddingAdapter.DEFAULT_SPEC.withBase(baseUrl),
                () -> settings(forcedFolder.orElse(null), settings.embedding()), Path.of(dataDir, "djl"));
    }

    static OnnxEmbeddingAdapter.Settings settings(String forced, EmbeddingSettings saved) {
        String folder = forced != null ? forced : saved.folder();
        if (folder == null) {
            return OnnxEmbeddingAdapter.Settings.DEFAULT;
        }
        PoolingMode pooling = saved.pooling() == EmbeddingSettings.Pooling.CLS ? PoolingMode.CLS : PoolingMode.MEAN;
        return new OnnxEmbeddingAdapter.Settings(folder, pooling, saved.queryPrefix(), saved.passagePrefix());
    }
}
