package dev.specwriter.domain.specification;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.specification.UseCaseChanges.Change;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** T120 : changements de cas d'usage entre deux versions (FR-047, research.md R29). */
class UseCaseChangesTest {

    static UseCase uc(String code, String title, String goal, String... steps) {
        return new UseCase(code, title, "client", goal, List.of("connecté"), List.of(steps), List.of("Annulation"), null);
    }

    static final UseCase PAYER = uc("UC-01", "Payer", "Régler son panier", "Choisir la carte", "Valider");
    static final UseCase REMBOURSER = uc("UC-02", "Rembourser", "Récupérer son argent", "Demander", "Recevoir");
    static final UseCase SUIVRE = uc("UC-03", "Suivre", "Suivre sa commande", "Ouvrir le suivi");

    @Test
    void casDUsageSupprimeOuModifie() {
        UseCase payerAutrement = uc("UC-01", "Payer", "Régler son panier", "Choisir la carte", "Saisir le code 3-D Secure",
                "Valider");
        Map<String, Change> changes = UseCaseChanges.between(List.of(PAYER, REMBOURSER, SUIVRE), List.of(payerAutrement, SUIVRE));
        assertThat(changes).containsExactlyInAnyOrderEntriesOf(Map.of("UC-01", Change.CHANGED, "UC-02", Change.REMOVED));
    }

    @Test
    void chaqueChampCompte() {
        List<UseCase> variants = List.of(
                new UseCase("UC-01", "Payer vite", "client", "Régler son panier", List.of("connecté"),
                        List.of("Choisir la carte", "Valider"), List.of("Annulation"), null),
                new UseCase("UC-01", "Payer", "invité", "Régler son panier", List.of("connecté"),
                        List.of("Choisir la carte", "Valider"), List.of("Annulation"), null),
                new UseCase("UC-01", "Payer", "client", "Régler", List.of("connecté"), List.of("Choisir la carte", "Valider"),
                        List.of("Annulation"), null),
                new UseCase("UC-01", "Payer", "client", "Régler son panier", List.of(), List.of("Choisir la carte", "Valider"),
                        List.of("Annulation"), null),
                new UseCase("UC-01", "Payer", "client", "Régler son panier", List.of("connecté"),
                        List.of("Choisir la carte", "Valider"), List.of(), null));
        for (UseCase variant : variants) {
            assertThat(UseCaseChanges.between(List.of(PAYER), List.of(variant))).containsEntry("UC-01", Change.CHANGED);
        }
    }

    @Test
    void espacesEtCasseIgnores() {
        UseCase same = new UseCase("UC-01", "  PAYER ", "Client", "régler  son panier", List.of("Connecté"),
                List.of("choisir la carte", " Valider"), List.of("annulation"), null);
        assertThat(UseCaseChanges.between(List.of(PAYER), List.of(same))).isEmpty();
    }

    @Test
    void renumerotageNEstPasUnChangement() {
        UseCase suivreRenumerote = uc("UC-02", "Suivre", "Suivre sa commande", "Ouvrir le suivi");
        assertThat(UseCaseChanges.between(List.of(PAYER, REMBOURSER, SUIVRE), List.of(PAYER, suivreRenumerote)))
                .containsExactlyEntriesOf(Map.of("UC-02", Change.REMOVED));
    }

    @Test
    void sansVersionPrecedenteAucunChangement() {
        assertThat(UseCaseChanges.between(List.of(), List.of(PAYER))).isEmpty();
    }
}
