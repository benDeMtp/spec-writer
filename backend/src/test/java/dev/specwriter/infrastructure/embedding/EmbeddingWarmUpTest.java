package dev.specwriter.infrastructure.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.support.FakeEmbedding;
import org.junit.jupiter.api.Test;

/**
 * Le modèle déjà présent sur le poste est chargé au démarrage, en tâche de fond : le premier besoin
 * de la journée est cherché par le sens, pas par les termes seuls (SC-002, vu en T119). Jamais de
 * téléchargement au démarrage.
 */
class EmbeddingWarmUpTest {

    static final class Local extends FakeEmbedding {
        boolean local = true;

        @Override
        public boolean availableLocally() {
            return local;
        }
    }

    @Test
    void modeleSurLePosteChargeAuDemarrage() throws InterruptedException {
        Local embedding = new Local();
        Thread warmUp = new EmbeddingWarmUp(embedding, true).start();
        assertThat(warmUp).isNotNull();
        warmUp.join(5_000);
        assertThat(embedding.state()).isEqualTo(EmbeddingPort.State.READY);
    }

    @Test
    void modeleAbsentOuDesactiveRienNEstFait() {
        Local absent = new Local();
        absent.local = false;
        assertThat(new EmbeddingWarmUp(absent, true).start()).isNull();
        assertThat(absent.state()).isEqualTo(EmbeddingPort.State.MISSING);

        Local disabled = new Local();
        assertThat(new EmbeddingWarmUp(disabled, false).start()).isNull();
        assertThat(disabled.state()).isEqualTo(EmbeddingPort.State.MISSING);
    }
}
