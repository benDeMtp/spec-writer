package dev.specwriter.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.domain.settings.EmbeddingSettings;
import dev.specwriter.support.FakeEmbedding;
import dev.specwriter.support.InMemorySettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** T115 : carte « Modèle de recherche » (research.md R19) : état, dossier de remplacement, pooling, préfixes. */
class ManageEmbeddingSettingsTest {

    final InMemorySettings settings = new InMemorySettings();
    final FakeEmbedding embedding = new FakeEmbedding();
    final ManageEmbeddingSettings manage = new ManageEmbeddingSettings(settings, embedding);

    @TempDir
    Path dir;

    @Test
    void modeleParDefautEtSonEtat() {
        embedding.state = EmbeddingPort.State.TERMS_ONLY;
        ManageEmbeddingSettings.View v = manage.get();
        assertThat(v.state()).isEqualTo(EmbeddingPort.State.TERMS_ONLY);
        assertThat(v.defaultModel()).isTrue();
        assertThat(v.folder()).isNull();
        assertThat(v.pooling()).isEqualTo(EmbeddingSettings.Pooling.MEAN);
        assertThat(v.modelId()).isEqualTo("fake@1");
    }

    @Test
    void dossierDeRemplacementChargeAussitot() throws IOException {
        Files.writeString(dir.resolve("model.onnx"), "x");
        Files.writeString(dir.resolve("tokenizer.json"), "{}");
        ManageEmbeddingSettings.View v = manage.save(dir.toString(), "CLS", "requête : ", "passage : ");
        assertThat(settings.embedding()).isEqualTo(new EmbeddingSettings(dir.toString(), EmbeddingSettings.Pooling.CLS,
                "requête : ", "passage : "));
        assertThat(v.state()).isEqualTo(EmbeddingPort.State.READY);
        assertThat(v.defaultModel()).isFalse();

        // revenir au modèle par défaut : rien n'est téléchargé tout de suite
        embedding.state = EmbeddingPort.State.MISSING;
        assertThat(manage.save(" ", null, null, null).defaultModel()).isTrue();
        assertThat(settings.embedding()).isEqualTo(EmbeddingSettings.DEFAULT);
        assertThat(embedding.state).isEqualTo(EmbeddingPort.State.MISSING);
    }

    static void refused(Runnable action, String reason) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.userMessage()).contains(reason));
    }

    @Test
    void dossierRefuseAvecUnMotif() throws IOException {
        refused(() -> manage.save("relatif/modele", null, null, null), "chemin absolu");
        refused(() -> manage.save(dir.resolve("absent").toString(), null, null, null), "n'existe pas");
        refused(() -> manage.save(dir.toString(), null, null, null), "model.onnx");
        Files.writeString(dir.resolve("model.onnx"), "x");
        refused(() -> manage.save(dir.toString(), null, null, null), "tokenizer.json");
        Files.writeString(dir.resolve("tokenizer.json"), "{}");
        refused(() -> manage.save(dir.toString(), "MAX", null, null), "MEAN ou CLS");
        refused(() -> manage.save(dir.toString(), null, "x".repeat(51), null), "50");
        assertThat(settings.embedding()).isEqualTo(EmbeddingSettings.DEFAULT);
    }
}
