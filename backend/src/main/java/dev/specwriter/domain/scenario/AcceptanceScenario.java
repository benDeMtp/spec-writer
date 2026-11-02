package dev.specwriter.domain.scenario;

import java.util.List;

/**
 * Scénario « En tant que / [Étant donné] / Quand / Alors » (contracts/scenario-format.md). Ce n'est
 * pas du Gherkin exécutable (FR-012a).
 */
public record AcceptanceScenario(
        String code,
        String useCaseCode,
        String title,
        Kind kind,
        String asA,
        List<String> given,
        List<String> when,
        List<String> then,
        boolean assumption) {

    public enum Kind {
        NOMINAL,
        ERROR,
        EDGE
    }

    public AcceptanceScenario {
        given = List.copyOf(given);
        when = List.copyOf(when);
        then = List.copyOf(then);
    }
}
