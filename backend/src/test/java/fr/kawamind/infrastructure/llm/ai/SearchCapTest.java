package fr.kawamind.infrastructure.llm.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.ToolInvocationContext;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Plafond des outils de l'agent (014, contracts/agent-tools.md) : 5 étapes, une analyse de périmètre. */
class SearchCapTest {

    private final SearchCap cap = new SearchCap();
    private final FakeTurn turn = new FakeTurn();

    private ToolInputGuardrailResult validate(String tool, boolean withParameters) {
        InvocationContext context = InvocationContext.builder().invocationParameters(withParameters
                ? InvocationParameters.from(Map.of(AgentTools.TURN, turn)) : new InvocationParameters()).build();
        return cap.validate(new ToolInputGuardrailRequest(ToolExecutionRequest.builder().id("1").name(tool)
                .arguments("{}").build(), null, new ToolInvocationContext(context)));
    }

    @Test
    void cinqEtapesPuisRefusAuSixiemeAppel() {
        for (int i = 0; i < 5; i++) {
            assertThat(validate("search_knowledge", true).isSuccess()).isTrue();
            turn.steps++;
        }
        ToolInputGuardrailResult sixth = validate("search_knowledge", true);
        assertThat(sixth.isSuccess()).isFalse();
        assertThat(sixth.isFatalFailure()).isFalse();
        assertThat(sixth.errorMessage()).isEqualTo(SearchCap.STOPPED);
    }

    @Test
    void uneSeuleAnalyseDePerimetre() {
        assertThat(validate("analyze_perimeter", true).isSuccess()).isTrue();
        turn.perimeterDone = true;
        ToolInputGuardrailResult second = validate("analyze_perimeter", true);
        assertThat(second.isSuccess()).isFalse();
        assertThat(second.errorMessage()).isEqualTo(SearchCap.PERIMETER_DONE);
        // la recherche reste possible
        assertThat(validate("search_knowledge", true).isSuccess()).isTrue();
    }

    @Test
    void leTourDuFilQuandLesParametresNArriventPas() {
        turn.steps = 5;
        assertThat(validate("search_knowledge", false).isSuccess()).isTrue();
        assertThat(ScopedValue.where(AgentTools.CURRENT, turn).call(() -> validate("search_knowledge", false))
                .errorMessage()).isEqualTo(SearchCap.STOPPED);
    }
}
