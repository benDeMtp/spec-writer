package dev.specwriter.infrastructure.embedding;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.infrastructure.embedding.ModelDownloader.ModelException;
import dev.specwriter.infrastructure.embedding.ModelDownloader.ModelFile;
import dev.specwriter.infrastructure.embedding.ModelDownloader.Spec;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Téléchargement du modèle d'embedding (T095, research.md R19). */
class ModelDownloaderTest {

    static final byte[] MODEL = "contenu du modèle ONNX ".repeat(5_000).getBytes(StandardCharsets.UTF_8);
    static final byte[] TOKENIZER = "{\"tokenizer\": true}".getBytes(StandardCharsets.UTF_8);

    WireMockServer server;
    @TempDir
    Path models;
    ModelDownloader downloader;
    Spec spec;

    static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    static EmbeddingPort.Progress progress(AtomicLong done) {
        return new EmbeddingPort.Progress() {
            @Override
            public void advanced(long bytes, long total) {
                done.set(bytes);
            }

            @Override
            public boolean cancelled() {
                return false;
            }
        };
    }

    @BeforeEach
    void demarrer() throws Exception {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        downloader = new ModelDownloader(HttpClient.newHttpClient(), models);
        spec = new Spec("e5-test", URI.create(server.baseUrl()), "Xenova/e5", "abc123",
                List.of(new ModelFile("onnx/model_quantized.onnx", sha(MODEL), MODEL.length),
                        new ModelFile("tokenizer.json", sha(TOKENIZER), TOKENIZER.length)));
    }

    @AfterEach
    void arreter() {
        server.stop();
    }

    void servir(byte[] model) {
        server.stubFor(get(urlEqualTo("/Xenova/e5/resolve/abc123/onnx/model_quantized.onnx"))
                .willReturn(aResponse().withStatus(200).withBody(model)));
        server.stubFor(get(urlEqualTo("/Xenova/e5/resolve/abc123/tokenizer.json"))
                .willReturn(aResponse().withStatus(200).withBody(TOKENIZER)));
    }

    @Test
    void telechargeDansLeDossierDuModeleEtVerifieLesEmpreintes() throws Exception {
        servir(MODEL);
        AtomicLong done = new AtomicLong();
        Path folder = downloader.ensure(spec, progress(done));

        assertThat(folder).isEqualTo(models.resolve("e5-test"));
        assertThat(folder.resolve("model_quantized.onnx")).hasBinaryContent(MODEL);
        assertThat(folder.resolve("tokenizer.json")).hasBinaryContent(TOKENIZER);
        assertThat(done.get()).isEqualTo(MODEL.length + TOKENIZER.length);
        assertThat(downloader.present(spec)).isTrue();

        downloader.ensure(spec, progress(new AtomicLong()));
        server.verify(1, getRequestedFor(urlEqualTo("/Xenova/e5/resolve/abc123/onnx/model_quantized.onnx")));
    }

    @Test
    void fichierAltereSupprimeEtSignale() {
        byte[] altered = Arrays.copyOf(MODEL, MODEL.length);
        altered[10] = 'X';
        servir(altered);
        assertThatThrownBy(() -> downloader.ensure(spec, progress(new AtomicLong())))
                .isInstanceOfSatisfying(ModelException.class,
                        e -> assertThat(e.reason()).isEqualTo(ModelException.Reason.CORRUPTED));
        assertThat(models.resolve("e5-test/model_quantized.onnx")).doesNotExist();
        assertThat(models.resolve("e5-test/model_quantized.onnx.part")).doesNotExist();
        assertThat(downloader.present(spec)).isFalse();
    }

    @Test
    void repriseApresUnTelechargementIncomplet() throws Exception {
        int already = 40_000;
        Path folder = Files.createDirectories(models.resolve("e5-test"));
        Files.write(folder.resolve("model_quantized.onnx.part"), Arrays.copyOf(MODEL, already));
        server.stubFor(get(urlEqualTo("/Xenova/e5/resolve/abc123/onnx/model_quantized.onnx"))
                .withHeader("Range", equalTo("bytes=" + already + "-"))
                .willReturn(aResponse().withStatus(206).withBody(Arrays.copyOfRange(MODEL, already, MODEL.length))));
        server.stubFor(get(urlEqualTo("/Xenova/e5/resolve/abc123/tokenizer.json"))
                .willReturn(aResponse().withStatus(200).withBody(TOKENIZER)));

        downloader.ensure(spec, progress(new AtomicLong()));
        assertThat(folder.resolve("model_quantized.onnx")).hasBinaryContent(MODEL);
    }

    @Test
    void serveurQuiIgnoreLaRepriseRepartDeZero() throws Exception {
        Path folder = Files.createDirectories(models.resolve("e5-test"));
        Files.write(folder.resolve("model_quantized.onnx.part"), Arrays.copyOf(MODEL, 1_000));
        servir(MODEL);
        downloader.ensure(spec, progress(new AtomicLong()));
        assertThat(folder.resolve("model_quantized.onnx")).hasBinaryContent(MODEL);
    }

    @Test
    void erreurHttpSignalee() {
        server.stubFor(get(urlEqualTo("/Xenova/e5/resolve/abc123/onnx/model_quantized.onnx"))
                .willReturn(aResponse().withStatus(404)));
        assertThatThrownBy(() -> downloader.ensure(spec, progress(new AtomicLong())))
                .isInstanceOfSatisfying(ModelException.class, e -> {
                    assertThat(e.reason()).isEqualTo(ModelException.Reason.HTTP_ERROR);
                    assertThat(e.status()).isEqualTo(404);
                });
    }

    @Test
    void interruptionGardeLeMorceauTelecharge() {
        servir(MODEL);
        EmbeddingPort.Progress cancelled = new EmbeddingPort.Progress() {
            @Override
            public void advanced(long bytes, long total) {
                // rien
            }

            @Override
            public boolean cancelled() {
                return true;
            }
        };
        assertThatThrownBy(() -> downloader.ensure(spec, cancelled))
                .isInstanceOfSatisfying(ModelException.class,
                        e -> assertThat(e.reason()).isEqualTo(ModelException.Reason.CANCELLED));
        assertThat(models.resolve("e5-test/model_quantized.onnx")).doesNotExist();
    }

    @Test
    void serveurInjoignable() {
        Spec unreachable = new Spec("e5-test", URI.create("http://127.0.0.1:1"), "Xenova/e5", "abc123", spec.files());
        assertThatThrownBy(() -> downloader.ensure(unreachable, progress(new AtomicLong())))
                .isInstanceOfSatisfying(ModelException.class,
                        e -> assertThat(e.reason()).isEqualTo(ModelException.Reason.UNREACHABLE));
    }

    /** Hugging Face redirige vers son CDN (302, adresse relative ou absolue) : la redirection est suivie. */
    @Test
    void redirectionsSuiviesJusquAuCdn() throws Exception {
        server.stubFor(get(urlEqualTo("/Xenova/e5/resolve/abc123/onnx/model_quantized.onnx"))
                .willReturn(aResponse().withStatus(302).withHeader("Location", "/cdn/model?sig=1")));
        server.stubFor(get(urlEqualTo("/cdn/model?sig=1"))
                .willReturn(aResponse().withStatus(307).withHeader("Location", server.baseUrl() + "/cdn2/model")));
        server.stubFor(get(urlEqualTo("/cdn2/model")).willReturn(aResponse().withStatus(200).withBody(MODEL)));
        server.stubFor(get(urlEqualTo("/Xenova/e5/resolve/abc123/tokenizer.json"))
                .willReturn(aResponse().withStatus(302).withHeader("Location", "../../../../cdn/tokenizer")));
        server.stubFor(get(urlEqualTo("/cdn/tokenizer")).willReturn(aResponse().withStatus(200).withBody(TOKENIZER)));

        Path folder = downloader.ensure(spec, progress(new AtomicLong()));
        assertThat(folder.resolve("model_quantized.onnx")).hasBinaryContent(MODEL);
        assertThat(folder.resolve("tokenizer.json")).hasBinaryContent(TOKENIZER);
    }

    @Test
    void boucleDeRedirectionsArretee() {
        server.stubFor(get(urlEqualTo("/Xenova/e5/resolve/abc123/onnx/model_quantized.onnx"))
                .willReturn(aResponse().withStatus(302).withHeader("Location", "/Xenova/e5/resolve/abc123/onnx/model_quantized.onnx")));
        assertThatThrownBy(() -> downloader.ensure(spec, progress(new AtomicLong())))
                .isInstanceOfSatisfying(ModelException.class, e -> {
                    assertThat(e.reason()).isEqualTo(ModelException.Reason.HTTP_ERROR);
                    assertThat(e.getMessage()).contains("redirections");
                });
    }
}
