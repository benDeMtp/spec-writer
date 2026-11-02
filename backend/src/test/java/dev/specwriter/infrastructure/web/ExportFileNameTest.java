package dev.specwriter.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExportFileNameTest {

    @Test
    void caracteresInterditsEtCheminsRetires() {
        assertThat(ExportFileName.of("Salles / ../du\\siège : été", 2)).isEqualTo("Salles-du-siège-été-v2.md");
        assertThat(ExportFileName.of("../../etc/passwd", 1)).isEqualTo("etc-passwd-v1.md");
        assertThat(ExportFileName.of("a*b?c\"d<e>f|g\u0007h", 1)).isEqualTo("a-b-c-d-e-f-g-h-v1.md");
        assertThat(ExportFileName.of("  ...  ", 3)).isEqualTo("specification-v3.md");
        assertThat(ExportFileName.of("Réservation de salles…", 1)).isEqualTo("Réservation-de-salles…-v1.md");
    }

    @Test
    void longueurBorneeEtRepliAscii() {
        assertThat(ExportFileName.of("x".repeat(300), 1)).hasSize(80 + "-v1.md".length());
        assertThat(ExportFileName.ascii("Réservation-été-v1.md")).isEqualTo("Reservation-ete-v1.md");
        assertThat(ExportFileName.ascii("日本-v1.md")).isEqualTo("__-v1.md");
    }
}
