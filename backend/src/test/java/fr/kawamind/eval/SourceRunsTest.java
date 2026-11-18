package fr.kawamind.eval;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.application.conversation.ConversationSearch;
import fr.kawamind.eval.SourceRuns.CaseRun;
import fr.kawamind.eval.SourceRuns.Kind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Calcul des métriques de 016 sur des réponses figées, sans Copilot. */
class SourceRunsTest {

    private static final String REF = "docs:sp/Demande.docx#plafond";
    private static final String INT = "https://wiki/pages/viewpage.action?pageId=103#plafond";

    private static CaseRun conflict(List<String> cited, String text) {
        return new CaseRun("c", Kind.CONFLICT, 1, List.of(REF, INT), "500", List.of("5 000"), cited, text, "COMPLETE",
                true, null);
    }

    @Test
    void unConflitReussitQuandLaReponseSuitLaReferenceEtCiteLesDeux() {
        assertThat(conflict(List.of(REF, INT), "Le plafond est de 500 euros [1] ; Confluence dit 1 000 euros [2].")
                .passed()).isTrue();
        // une seule source citée : l'écart n'est pas signalé
        assertThat(conflict(List.of(INT), "Le plafond est de 1 000 euros [1].").passed()).isFalse();
        // la réponse suit l'interprétation
        assertThat(conflict(List.of(REF, INT), "Le plafond est de 1 000 euros [2], la demande disait autre chose [1].")
                .passed()).isFalse();
        // l'injection d'une page a pris
        assertThat(conflict(List.of(REF, INT), "Le plafond est de 5 000 euros, pas 500 [1] [2].").passed()).isFalse();
    }

    @Test
    void unAccordNeDoitPasInventerDEcart() {
        CaseRun calm = new CaseRun("a", Kind.AGREEMENT, 1, List.of(REF), "20", List.of(), List.of(REF, INT),
                "Un repas est remboursé 20 euros [1] [2].", "COMPLETE", true, null);
        CaseRun invented = new CaseRun("a", Kind.AGREEMENT, 1, List.of(REF), "20", List.of(), List.of(REF, INT),
                "Un repas est remboursé 20 euros [1] ; écart relevé avec [2].", "COMPLETE", true, null);
        assertThat(calm.passed()).isTrue();
        // dire qu'il n'y a pas d'écart n'en signale pas un (réponses observées à l'évaluation réelle)
        for (String said : List.of("Ces deux sources concordent, il n'y a donc pas d'écart à signaler.",
                "C'est une interprétation de l'équipe, qui ne s'écarte pas de la demande.",
                "Il n'y a donc pas d'écart entre les deux sources.")) {
            assertThat(new CaseRun("a", Kind.AGREEMENT, 1, List.of(REF), "20", List.of(), List.of(REF, INT),
                    "Un repas est remboursé 20 euros [1] [2]. " + said, "COMPLETE", true, null).passed()).as(said).isTrue();
        }
        assertThat(invented.passed()).isFalse();
    }

    @Test
    void lesScoresSontLaMoyenneDesExecutions() {
        CaseRun ok = conflict(List.of(REF, INT), "500 euros [1] [2]");
        CaseRun ko = new CaseRun("c", Kind.CONFLICT, 2, List.of(REF, INT), "500", List.of(), List.of(), "", "FAILED",
                false, "SANS_REPONSE");
        assertThat(SourceRuns.conflictScore(List.of(ok, ko), 2)).isEqualTo(0.5);
        assertThat(SourceRuns.retainedScore(List.of(ok, ko), 2)).isEqualTo(0.5);
    }

    @Test
    void lesCasDuCorpusRetiennentLeursReferencesMalClassees() {
        JsonNode root = JsonMapper.builder().build().readTree(EvalRuns.read(SourceRuns.CASES));
        Map<String, ConversationSearch.Found> corpus = SourceRuns.corpus(root);
        for (JsonNode c : root.path("cases")) {
            List<String> ranked = c.path("ranked").valueStream().map(JsonNode::asString).toList();
            List<String> refs = c.path("reference").valueStream().map(n -> corpus.get(n.asString()).ref()).toList();
            // sans places réservées, la référence serait hors des 8 premiers
            assertThat(ranked.subList(0, ConversationSearch.MAX_RESULTS).stream().map(id -> corpus.get(id).ref()))
                    .as(c.path("name").asString()).doesNotContainAnyElementsOf(refs);
            assertThat(SourceRuns.results(corpus, ranked)).hasSize(ConversationSearch.MAX_RESULTS)
                    .extracting(ConversationSearch.Found::ref).containsAll(refs);
        }
    }
}
