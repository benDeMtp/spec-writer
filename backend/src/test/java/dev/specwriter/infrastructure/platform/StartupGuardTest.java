package dev.specwriter.infrastructure.platform;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.quarkus.runtime.LaunchMode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StartupGuardTest {

    static final Optional<String> REPLAY = Optional.of("/tmp/replay");

    @Test
    void modesDeTestRefusesEnProduction() {
        assertThatThrownBy(() -> StartupGuard.check(LaunchMode.NORMAL, List.of("prod"), true, Optional.empty()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("specwriter.github.fake");
        assertThatThrownBy(() -> StartupGuard.check(LaunchMode.NORMAL, List.of("prod"), false, REPLAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("specwriter.llm.replay-dir");
    }

    @Test
    void leProfilDevNeSuffitPasDansLeJarInstalle() {
        assertThatThrownBy(() -> StartupGuard.check(LaunchMode.NORMAL, List.of("dev"), true, REPLAY))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> StartupGuard.check(LaunchMode.NORMAL, List.of("dev", "live"), false, REPLAY))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void modesDeTestAcceptesEnDevModeEnTestEtEnE2e() {
        assertThatCode(() -> StartupGuard.check(LaunchMode.DEVELOPMENT, List.of("dev"), true, REPLAY))
                .doesNotThrowAnyException();
        assertThatCode(() -> StartupGuard.check(LaunchMode.TEST, List.of("test"), true, Optional.empty()))
                .doesNotThrowAnyException();
        assertThatCode(() -> StartupGuard.check(LaunchMode.NORMAL, List.of("e2e"), true, REPLAY))
                .doesNotThrowAnyException();
    }

    @Test
    void productionSansModeDeTest() {
        assertThatCode(() -> StartupGuard.check(LaunchMode.NORMAL, List.of("prod"), false, Optional.empty()))
                .doesNotThrowAnyException();
        assertThatCode(() -> StartupGuard.check(LaunchMode.NORMAL, List.of("prod"), false, Optional.of(" ")))
                .doesNotThrowAnyException();
    }
}
