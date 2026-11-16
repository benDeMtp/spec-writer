package fr.kawamind.infrastructure.llm.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.llm.CompareIssueRequest;
import fr.kawamind.domain.llm.ImpactRequest;
import fr.kawamind.domain.llm.ImpactResult;
import fr.kawamind.domain.llm.IssueComparison;
import fr.kawamind.domain.llm.RelatedIssuesJudgement;
import fr.kawamind.domain.llm.RelatedIssuesRequest;
import fr.kawamind.domain.port.LlmClient;
import fr.kawamind.domain.session.Language;
import fr.kawamind.support.ScriptedChatModel;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Famille Knowledge sur les AI Services (014, US1, research R3, R4) : chaque opération rend l'objet
 * attendu avec le schéma strict de sa réponse ; une sortie non conforme est redemandée, deux fois
 * au plus.
 */
@QuarkusTest
class KnowledgeAiTest {

    static final String NEED = "IT-PROCHES Le gestionnaire doit pouvoir annuler une facture émise par un avoir total.";
    static final RelatedIssuesRequest RELATED = new RelatedIssuesRequest(Language.FR, NEED,
            List.of(new RelatedIssuesRequest.Candidate("FACT-7", "Annuler une facture", "Terminé", "Avoir total")));

    @Inject
    LlmClient llm;

    @Test
    void ticketsProchesEtComparaisonSurLeRejeu() {
        RelatedIssuesJudgement judgement = llm.judgeRelatedIssues(RELATED);
        assertThat(judgement.related()).extracting(RelatedIssuesJudgement.Related::key).containsExactly("FACT-7", "FACT-9");
        IssueComparison comparison = llm.compareWithIssue(new CompareIssueRequest(Language.FR, NEED, "FACT-9",
                "Avoir sur ligne", "Une ligne", "À faire"));
        assertThat(comparison.suggestion()).isEqualTo(IssueComparison.Suggestion.DIFFERENT);
    }

    @Test
    void chaqueOperationTransmetSonSchemaEtSaConsigne() {
        ScriptedChatModel model = new ScriptedChatModel()
                .then("judge-related-issues", List.of("<candidats>"), "{\"related\":[]}")
                .then("compare-with-issue", List.of("<ticket ref="),
                        "{\"common\":[],\"different\":[\"Une ligne\"],\"suggestion\":\"DIFFERENT\"}")
                .then("identify-impact", List.of("<elements>"),
                        "{\"modules\":[\"Facturation/API\"],\"exchanges\":[{\"from\":\"Facturation/API\","
                                + "\"to\":\"Comptabilite\",\"reason\":\"Notifier\"}]}");
        QuarkusMock.installMockForType(model, ChatModel.class);

        assertThat(llm.judgeRelatedIssues(RELATED).related()).isEmpty();
        llm.compareWithIssue(new CompareIssueRequest(Language.FR, NEED, "FACT-9", "t", "d", "À faire"));
        ImpactResult impact = llm.identifyImpact(new ImpactRequest(Language.FR, NEED, List.of(),
                List.of(new ImpactRequest.Element("Facturation/API", "CONTAINER", "API", "Java", "PROJECT"))));
        assertThat(impact.modules()).containsExactly("Facturation/API");

        assertThat(model.requests()).extracting(r -> r.parameters().responseFormat().jsonSchema().name())
                .containsExactly("related_issues", "issue_comparison", "impact");
        for (ChatRequest r : model.requests()) {
            assertThat(r.parameters().toolSpecifications()).isNullOrEmpty();
        }
        assertThat(model.received()).allSatisfy(c -> assertThat(c.system()).startsWith("[operation:")
                .doesNotContain(NEED));
        assertThat(model.exhausted()).isTrue();
    }

    @Test
    void uneSortieNonConformeEstRedemandee() {
        ScriptedChatModel model = new ScriptedChatModel()
                .then("compare-with-issue", List.of(), "pas du JSON")
                .then("compare-with-issue", List.of("La réponse précédente n'était pas un JSON conforme"),
                        "{\"common\":[],\"different\":[],\"suggestion\":\"SAME\"}");
        QuarkusMock.installMockForType(model, ChatModel.class);
        IssueComparison comparison = llm.compareWithIssue(new CompareIssueRequest(Language.FR, NEED, "FACT-9", "t",
                "d", "À faire"));
        assertThat(comparison.suggestion()).isEqualTo(IssueComparison.Suggestion.SAME);
        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().get(1).parameters().responseFormat()).isNotNull();
    }

    @Test
    void troisSortiesNonConformesDonnentUneErreurMetier() {
        ScriptedChatModel model = new ScriptedChatModel();
        for (int i = 0; i < 3; i++) {
            model.then("judge-related-issues", List.of(), "{\"related\":[{\"key\":\"\",\"reason\":\"sans clé\"}]}");
        }
        QuarkusMock.installMockForType(model, ChatModel.class);
        assertThatThrownBy(() -> llm.judgeRelatedIssues(RELATED)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.LLM_INVALID_OUTPUT));
        assertThat(model.exhausted()).isTrue();
    }

    @Test
    void uneErreurDuModeleRestelErreurMetierDuModele() {
        QuarkusMock.installMockForType(new ScriptedChatModel() {
            @Override
            public dev.langchain4j.model.chat.response.ChatResponse doChat(ChatRequest request) {
                throw new DomainException(ErrorCode.QUOTA_EXHAUSTED);
            }
        }, ChatModel.class);
        assertThatThrownBy(() -> llm.judgeRelatedIssues(RELATED)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.QUOTA_EXHAUSTED));
    }
}
