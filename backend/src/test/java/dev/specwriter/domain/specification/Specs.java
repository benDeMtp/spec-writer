package dev.specwriter.domain.specification;

import dev.specwriter.domain.scenario.AcceptanceScenario;
import java.util.List;
import java.util.UUID;

/** Contenus de spécification de test. */
public final class Specs {

    public static final UUID Q1 = new UUID(0, 1);

    private Specs() {
    }

    public static UseCase useCase(String code, SourceRef source) {
        return new UseCase(code, "Payer la commande", "client connecté", "Régler son panier", List.of("Panier non vide"),
                List.of("Le client valide le panier", "Il paie"), List.of(), source);
    }

    public static AcceptanceScenario scenario(String code, String useCase, AcceptanceScenario.Kind kind) {
        return new AcceptanceScenario(code, useCase, "Paiement accepté", kind, "client connecté",
                List.of("un panier de 3 articles"), List.of("je valide le paiement"), List.of("la commande est confirmée"),
                false);
    }

    public static SpecificationContent valid() {
        return new SpecificationContent("Paiement en ligne", "Le client paie sa commande.",
                List.of(useCase("UC-01", new SourceRef(SourceRef.Kind.NEED_EXCERPT, null, "payer en ligne"))),
                List.of(scenario("SC-01-01", "UC-01", AcceptanceScenario.Kind.NOMINAL)), List.of(), List.of());
    }

    public static SpecificationContent with(List<UseCase> useCases, List<AcceptanceScenario> scenarios,
            List<Assumption> assumptions) {
        return new SpecificationContent("Paiement en ligne", "Le client paie sa commande.", useCases, scenarios,
                assumptions, List.of());
    }
}
