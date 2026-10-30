package dev.specwriter.infrastructure.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.ServerSocket;
import io.quarkus.runtime.LaunchMode;
import org.junit.jupiter.api.Test;

class PortSelectorTest {

    @Test
    void portConfigureLibreUtilise() throws Exception {
        int libre;
        try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            libre = s.getLocalPort();
        }
        assertThat(PortSelector.choose(libre)).isEqualTo(libre);
    }

    @Test
    void portOccupeRemplaceParUnPortLibre() throws Exception {
        try (ServerSocket occupe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            int choisi = PortSelector.choose(occupe.getLocalPort());
            assertThat(choisi).isNotEqualTo(occupe.getLocalPort()).isPositive();
        }
    }

    @Test
    void enDevModeLePortConfigureEstGardeMemeOccupe() throws Exception {
        // le dev mode garde son serveur HTTP d'un rechargement à l'autre : le port occupé est le sien
        try (ServerSocket occupe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            assertThat(PortSelector.choose(occupe.getLocalPort(), LaunchMode.DEVELOPMENT)).isEqualTo(occupe.getLocalPort());
            assertThat(PortSelector.choose(occupe.getLocalPort(), LaunchMode.NORMAL)).isNotEqualTo(occupe.getLocalPort());
        }
    }
}
