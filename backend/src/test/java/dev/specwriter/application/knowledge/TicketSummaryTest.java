package dev.specwriter.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Bilan de l'étape Jira (FR-009 de 003). */
class TicketSummaryTest {

    @Test
    void nombresPositifsOuNuls() {
        assertThat(new TicketSummary(20, 80, 0).keptClosed()).isEqualTo(80);
    }

    @Test
    void nombreNegatifRefuse() {
        assertThatThrownBy(() -> new TicketSummary(-1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TicketSummary(0, -1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TicketSummary(0, 0, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
