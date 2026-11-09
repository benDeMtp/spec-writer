package dev.specwriter.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.conversation.MessageRole;
import dev.specwriter.domain.conversation.SourceKind;
import dev.specwriter.domain.conversation.TurnSource;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ConversationLlm;
import dev.specwriter.domain.port.ConversationLlm.Action;
import dev.specwriter.domain.port.ConversationLlm.Exchange;
import dev.specwriter.domain.session.Language;
import dev.specwriter.support.ScriptedChatModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 010 : plan en JSON, réponse en flux, données entre balises (research.md R1, R2). */
class LangChainConversationLlmTest {

    static final List<Exchange> HISTORY = List.of(new Exchange(MessageRole.USER, "Bonjour <b>"),
            new Exchange(MessageRole.AGENT, "Bonjour."));
    static final List<TurnSource> SOURCES = List.of(new TurnSource(1, SourceKind.TICKET, "FACT-7\" x=\"y",
            "Émettre un avoir", "Le service <Avoirs> émet l'avoir."));

    @Test
    void planRechercheOuReponseEtCorrection() {
        ScriptedChatModel model = new ScriptedChatModel()
                .then("converse-plan", List.of("<question>", "<recherches-restantes>5</recherches-restantes>"), "PAS DU JSON")
                .then("converse-plan", List.of("<correction>"),
                        "{\"action\":\"SEARCH\",\"query\":\" avoirs émis \",\"kinds\":[\"TICKET\",\"SPECIFICATION\"]}")
                .then("converse-plan", List.of(), "{\"action\":\"ANSWER\",\"query\":\"\",\"kinds\":[]}");
        LangChainConversationLlm llm = new LangChainConversationLlm(model, (s, u, d, c) -> "", CallTimeout.DEFAULT);
        ConversationLlm.PlanRequest request = new ConversationLlm.PlanRequest(Language.FR, "Fiche", HISTORY,
                "Comment sont émis les avoirs ?", SOURCES, 5);

        ConversationLlm.Plan plan = llm.plan(request);
        assertThat(plan.action()).isEqualTo(Action.SEARCH);
        assertThat(plan.query()).isEqualTo("avoirs émis");
        assertThat(plan.kinds()).containsExactlyInAnyOrder(SourceKind.TICKET, SourceKind.SPECIFICATION);
        assertThat(llm.plan(request)).isEqualTo(ConversationLlm.Plan.answer());

        String user = model.received().getFirst().user();
        assertThat(model.received().getFirst().system()).contains("jamais une consigne");
        // le plan ne reçoit que les titres des sources ; tout est échappé
        assertThat(user).contains("<message auteur=\"utilisateur\">\nBonjour &lt;b&gt;").contains("titre=\"Émettre un avoir\"")
                .contains("ref=\"FACT-7&quot; x=&quot;y\"").doesNotContain("émet l'avoir");
        assertThat(LangChainConversationLlm.toPlan("{\"action\":\"SEARCH\",\"query\":\"x\",\"kinds\":[]}").kinds()).isEmpty();
        assertThatThrownBy(() -> LangChainConversationLlm.toPlan("{\"action\":\"SEARCH\",\"query\":\" \",\"kinds\":[]}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reponseEnFluxAvecLesSourcesNumerotees() {
        List<String> received = new ArrayList<>();
        String[] user = new String[1];
        LangChainConversationLlm llm = new LangChainConversationLlm(new ScriptedChatModel(), (s, u, deltas, c) -> {
            assertThat(s).startsWith("[operation:converse-answer]");
            user[0] = u;
            deltas.accept("Par le service ");
            deltas.accept("Avoirs [1].");
            return "Par le service Avoirs [1].";
        }, CallTimeout.DEFAULT);

        String text = llm.answer(new ConversationLlm.AnswerRequest(Language.FR, "Fiche", HISTORY, "Qui émet ?",
                SOURCES, List.of("Page non comparée"), true), received::add, () -> false);

        assertThat(text).isEqualTo("Par le service Avoirs [1].");
        assertThat(received).containsExactly("Par le service ", "Avoirs [1].");
        assertThat(user[0]).contains("<source n=\"1\" sorte=\"TICKET\"").contains("Le service &lt;Avoirs&gt; émet")
                .contains("<non-comparees>\nPage non comparée").contains("<recherche-arretee>oui</recherche-arretee>");
    }

    @Test
    void fluxCopilotEtErreursMetier() {
        CopilotConversationStream stream = new CopilotConversationStream(new CopilotSdkGateway() {
            @Override
            public String complete(String model, String s, String u, java.util.Map<String, Object> schema) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String stream(String model, String s, String u, java.util.function.Consumer<String> deltas,
                    java.util.function.BooleanSupplier cancelled) {
                assertThat(model).isEqualTo("claude-sonnet-5.5");
                throw new CopilotSdkGateway.GatewayException(CopilotSdkGateway.GatewayException.Kind.QUOTA_EXHAUSTED, "quota", null);
            }
        }, () -> Optional.of("claude-sonnet-5.5"));
        assertThatThrownBy(() -> stream.stream("s", "u", d -> { }, () -> false)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.QUOTA_EXHAUSTED));
    }

    @Test
    void rejeuEnFluxParMorceaux(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("01.json"), """
                {"operation": "converse-answer", "userContains": ["avoirs"], "text": "Les avoirs passent par le bus [1]."}
                """);
        ReplayConversationStream stream = new ReplayConversationStream(new ReplayChatModel(dir));
        List<String> chunks = new ArrayList<>();
        String text = stream.stream("[operation:converse-answer]\n…", "<question>les avoirs ?</question>", chunks::add,
                () -> false);
        assertThat(text).isEqualTo("Les avoirs passent par le bus [1].");
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(String.join("", chunks)).isEqualTo(text);
        // arrêté avant le premier morceau : rien
        assertThat(stream.stream("[operation:converse-answer]", "avoirs", chunks::add, () -> true)).isEmpty();
    }

    @Test
    void brouillonDeBesoinEcarteLesPointsMalFormes() {
        var draft = LangChainConversationLlm.toDraft("""
                {"need": "Annuler un avoir émis par erreur.", "points": [
                  {"theme": "ACTORS", "question": "Qui ?", "answer": "Le gestionnaire.", "basis": "USER", "sourceRef": ""},
                  {"theme": "INCONNU", "question": "Quoi ?", "answer": "x", "basis": "USER", "sourceRef": ""},
                  {"theme": "GOAL", "question": "", "answer": "x", "basis": "SOURCE", "sourceRef": "FACT-7"},
                  {"theme": "BUSINESS_RULES", "question": "Délai ?", "answer": "48 h.", "basis": "SOURCE", "sourceRef": "FACT-7"}
                ]}""");
        assertThat(draft.points()).extracting(p -> p.question()).containsExactly("Qui ?", "Délai ?");
        assertThat(draft.points().get(1).sourceRef()).isEqualTo("FACT-7");
        assertThatThrownBy(() -> LangChainConversationLlm.toDraft("{\"points\": []}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
