package dev.specwriter.infrastructure.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.onnx.OnnxEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.PoolingMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Mise en route du moteur d'embedding (T089, research.md R19) : onnxruntime et les tokenizers DJL
 * se chargent depuis le classpath, sans téléchargement, et un vecteur se calcule vite.
 */
class OnnxEmbeddingSmokeIT {

    static OnnxEmbeddingModel model;

    @BeforeAll
    static void load() throws IOException {
        System.setProperty("ai.djl.offline", "true");
        Path folder = Path.of(System.getProperty("specwriter.test-model", "target/test-model"));
        model = new OnnxEmbeddingModel(find(folder, ".onnx").toString(), find(folder, "tokenizer.json").toString(),
                PoolingMode.MEAN);
    }

    static Path find(Path folder, String suffix) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(f -> f.getFileName().toString().endsWith(suffix)).findFirst().orElseThrow();
        }
    }

    static double cosine(Embedding a, Embedding b) {
        float[] x = a.vector();
        float[] y = b.vector();
        double dot = 0;
        for (int i = 0; i < x.length; i++) {
            dot += x[i] * y[i];
        }
        return dot;
    }

    @Test
    void vecteursNormalisesEtProchesParLeSens() {
        Embedding invoice = model.embed("cancel an invoice").content();
        Embedding credit = model.embed("issue a credit note for an invoice").content();
        Embedding weather = model.embed("the weather is sunny at the beach").content();

        double norm = Math.sqrt(cosine(invoice, invoice));
        assertThat(norm).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-3));
        assertThat(cosine(invoice, credit)).isGreaterThan(cosine(invoice, weather));
    }

    @Test
    void unVecteurEnMoinsDe50Ms() {
        model.embed("échauffement").content();
        long start = System.nanoTime();
        for (int i = 0; i < 20; i++) {
            model.embed("Le gestionnaire de facturation émet un avoir sur une facture émise " + i).content();
        }
        long perVector = (System.nanoTime() - start) / 20 / 1_000_000;
        assertThat(perVector).isLessThan(50);
    }
}
