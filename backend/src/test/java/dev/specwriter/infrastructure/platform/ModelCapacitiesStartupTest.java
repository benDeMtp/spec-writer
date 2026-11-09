package dev.specwriter.infrastructure.platform;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.application.settings.RefreshModelCapacities;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ModelCapacitiesStartupTest {

    @Test
    void relectureEnTacheDeFondSansPropagerLEchec() throws InterruptedException {
        AtomicInteger calls = new AtomicInteger();
        RefreshModelCapacities failing = new RefreshModelCapacities(null, null) {
            @Override
            public boolean run() {
                calls.incrementAndGet();
                throw new IllegalStateException("Copilot absent");
            }
        };
        Thread thread = new ModelCapacitiesStartup(failing).start();
        thread.join(5_000);
        assertThat(thread.isAlive()).isFalse();
        assertThat(calls).hasValue(1);
    }
}
