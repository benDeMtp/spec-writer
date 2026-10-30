package dev.specwriter.infrastructure.platform;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StartupGuardTest {

    @Test
    void modesDeTestRefusesEnProduction() {
        assertThatThrownBy(() -> StartupGuard.check(List.of("prod"), true, Optional.empty()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("specwriter.github.fake");
        assertThatThrownBy(() -> StartupGuard.check(List.of("prod"), false, Optional.of("/tmp/replay")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("specwriter.llm.replay-dir");
    }

    @Test
    void modesDeTestAcceptesEnTestEtE2e() {
        assertThatCode(() -> StartupGuard.check(List.of("e2e"), true, Optional.of("/tmp/replay"))).doesNotThrowAnyException();
        assertThatCode(() -> StartupGuard.check(List.of("test"), true, Optional.empty())).doesNotThrowAnyException();
    }

    @Test
    void productionSansModeDeTest() {
        assertThatCode(() -> StartupGuard.check(List.of("prod"), false, Optional.empty())).doesNotThrowAnyException();
    }
}
