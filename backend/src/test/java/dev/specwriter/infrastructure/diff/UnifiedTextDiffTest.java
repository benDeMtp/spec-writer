package dev.specwriter.infrastructure.diff;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UnifiedTextDiffTest {

    final UnifiedTextDiff diff = new UnifiedTextDiff();

    @Test
    void diffUnifieAvecEnTetesEtLignesModifiees() {
        String out = diff.unified("# Titre\nun\ndeux\n", "# Titre\nun\ntrois\n", "v1", "proposition");
        assertThat(out).startsWith("--- v1\n+++ proposition\n@@ ").contains("\n-deux\n").contains("\n+trois\n")
                .contains("\n un\n");
    }

    @Test
    void textesIdentiquesOuVides() {
        assertThat(diff.unified("a\nb", "a\nb", "v1", "p")).isEmpty();
        assertThat(diff.unified(null, "a", "v1", "p")).contains("+a");
    }
}
