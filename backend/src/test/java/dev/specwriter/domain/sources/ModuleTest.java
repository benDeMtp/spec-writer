package dev.specwriter.domain.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModuleTest {

    static final UUID ROOT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void lIdentifiantEstLaRacineEtLeCheminRelatif() {
        Module module = new Module(ROOT, "libs\\common-contract", "common-contract", ModuleKind.MAVEN, "fr.demo:common-contract");
        assertThat(module.path()).isEqualTo("libs/common-contract");
        assertThat(module.id().toString()).isEqualTo(ROOT + "/libs/common-contract");
        assertThat(ModuleId.parse(module.id().toString())).isEqualTo(module.id());
    }

    @Test
    void lePseudoModuleDeLaRacineAUnCheminVide() {
        Module root = Module.root(ROOT, "rv");
        assertThat(root.kind()).isEqualTo(ModuleKind.ROOT);
        assertThat(root.path()).isEmpty();
        assertThat(root.name()).isEqualTo("rv");
        assertThat(root.id().toString()).isEqualTo(ROOT + "/");
    }

    @Test
    void leNomEstBorne() {
        assertThatThrownBy(() -> new Module(ROOT, "a", " ", ModuleKind.NPM, null)).isInstanceOf(DomainException.class);
        assertThat(new Module(ROOT, "a", "x".repeat(250), ModuleKind.NPM, null).name()).hasSize(Module.MAX_NAME);
    }

    @Test
    void unCheminQuiSortDeLaRacineEstRefuse() {
        assertThatThrownBy(() -> new Module(ROOT, "../ailleurs", "x", ModuleKind.MAVEN, null))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new Module(ROOT, "/abs", "x", ModuleKind.MAVEN, null)).isInstanceOf(DomainException.class);
    }

    @Test
    void lesCoordonneesNeValentQuePourMaven() {
        assertThat(new Module(ROOT, "front", "demo-front", ModuleKind.NPM, "x:y").coordinates()).isNull();
    }
}
