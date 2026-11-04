package dev.specwriter.infrastructure.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.embedding.onnx.OnnxEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.PoolingMode;
import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.infrastructure.embedding.OnnxEmbeddingAdapter.Settings;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Adaptateur d'embedding sur le modèle de test (T095, research.md R19). */
class OnnxEmbeddingAdapterIT {

    static final Path TEST_MODEL = Path.of(System.getProperty("specwriter.test-model", "target/test-model"));

    @TempDir
    Path data;

    static final EmbeddingPort.Progress NONE = new EmbeddingPort.Progress() {
        @Override
        public void advanced(long done, long total) {
            // rien
        }

        @Override
        public boolean cancelled() {
            return false;
        }
    };

    /** Téléchargeur qui échoue s'il est appelé : le dossier déclaré doit l'éviter. */
    OnnxEmbeddingAdapter adapter(Settings settings) {
        ModelDownloader unreachable = new ModelDownloader(HttpClient.newHttpClient(), data.resolve("models"));
        return new OnnxEmbeddingAdapter(unreachable, OnnxEmbeddingAdapter.DEFAULT_SPEC.withBase(
                URI.create("http://127.0.0.1:1")), () -> settings, data.resolve("djl"));
    }

    static double dot(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    @Test
    void dossierDeclareSansTelechargementEtPrefixesAppliques() throws Exception {
        OnnxEmbeddingAdapter adapter = adapter(new Settings(TEST_MODEL.toString(), PoolingMode.MEAN, "query: ",
                "passage: "));
        assertThat(adapter.state()).isEqualTo(EmbeddingPort.State.MISSING);
        assertThat(adapter.prepare(NONE)).isEqualTo(EmbeddingPort.State.READY);
        assertThat(adapter.modelId()).startsWith("local:test-model@");
        assertThat(Files.exists(data.resolve("models"))).isFalse();

        OnnxEmbeddingModel raw = new OnnxEmbeddingModel(OnnxEmbeddingSmokeIT.find(TEST_MODEL, ".onnx").toString(),
                OnnxEmbeddingSmokeIT.find(TEST_MODEL, "tokenizer.json").toString(), PoolingMode.MEAN);
        float[] query = adapter.query("annuler une facture");
        assertThat(query).containsExactly(raw.embed("query: annuler une facture").content().vector(), Offset.offset(1e-5f));
        List<float[]> passages = adapter.passages(List.of("annuler une facture", "émettre un avoir"));
        assertThat(passages).hasSize(2);
        assertThat(passages.get(0)).containsExactly(raw.embed("passage: annuler une facture").content().vector(),
                Offset.offset(1e-5f));
        assertThat(Math.sqrt(dot(query, query))).isCloseTo(1.0, Offset.offset(1e-3));
    }

    @Test
    void lesReglagesChangentLIdentifiantDuModele() {
        OnnxEmbeddingAdapter mean = adapter(new Settings(TEST_MODEL.toString(), PoolingMode.MEAN, "query: ", "passage: "));
        OnnxEmbeddingAdapter cls = adapter(new Settings(TEST_MODEL.toString(), PoolingMode.CLS, "query: ", "passage: "));
        OnnxEmbeddingAdapter noPrefix = adapter(new Settings(TEST_MODEL.toString(), PoolingMode.MEAN, "", ""));
        mean.prepare(NONE);
        cls.prepare(NONE);
        noPrefix.prepare(NONE);
        assertThat(List.of(mean.modelId(), cls.modelId(), noPrefix.modelId())).doesNotHaveDuplicates();
    }

    @Test
    void modeleAbsentDonneLaRechercheParLesTermesSansException() {
        OnnxEmbeddingAdapter adapter = adapter(new Settings(data.resolve("absent").toString(), PoolingMode.MEAN, "", ""));
        assertThat(adapter.prepare(NONE)).isEqualTo(EmbeddingPort.State.TERMS_ONLY);
        assertThat(adapter.query("facture")).isNull();
        assertThat(adapter.passages(List.of("facture"))).isEmpty();
        assertThat(adapter.problem()).isNotBlank();
    }

    @Test
    void modeleIllisibleDonneLaRechercheParLesTermes() throws Exception {
        Path folder = Files.createDirectories(data.resolve("illisible"));
        Files.writeString(folder.resolve("model.onnx"), "pas un modèle");
        Files.writeString(folder.resolve("tokenizer.json"), "{}");
        OnnxEmbeddingAdapter adapter = adapter(new Settings(folder.toString(), PoolingMode.MEAN, "", ""));
        assertThat(adapter.prepare(NONE)).isEqualTo(EmbeddingPort.State.TERMS_ONLY);
    }

    @Test
    void telechargementImpossibleDonneLaRechercheParLesTermes() {
        OnnxEmbeddingAdapter adapter = adapter(Settings.DEFAULT);
        assertThat(adapter.prepare(NONE)).isEqualTo(EmbeddingPort.State.TERMS_ONLY);
        assertThat(adapter.modelId()).isEqualTo("Xenova/multilingual-e5-small@761b726dd34fb83930e26aab4e9ac3899aa1fa78");
    }
}
