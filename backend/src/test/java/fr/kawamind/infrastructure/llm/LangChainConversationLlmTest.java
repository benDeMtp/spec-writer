package fr.kawamind.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.conversation.MessageRole;
import fr.kawamind.domain.conversation.SourceKind;
import fr.kawamind.domain.conversation.TurnSource;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.ConversationLlm;
import fr.kawamind.domain.port.ConversationLlm.Exchange;
import fr.kawamind.domain.port.TurnTools;
import fr.kawamind.infrastructure.llm.ai.LlmOutputs;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkus.test.junit.QuarkusMock;
import fr.kawamind.domain.session.Language;
import fr.kawamind.support.ScriptedChatModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 010, 014 : agent à outils en flux, opérations structurées, données entre balises (research R5). */
@io.quarkus.test.junit.QuarkusTest
class LangChainConversationLlmTest {

    /** Port de l'application ; les opérations migrées passent par les AI Services (014). */
    @jakarta.inject.Inject
    ConversationLlm conversation;

    private ConversationLlm bean(dev.langchain4j.model.chat.ChatModel model) {
        io.quarkus.test.junit.QuarkusMock.installMockForType(model, dev.langchain4j.model.chat.ChatModel.class);
        return conversation;
    }

    private static List<ConversationLlm.ChangeCandidate> changes(String json) {
        return fr.kawamind.infrastructure.llm.ai.LlmOutputs.changes(json).changes();
    }

    static final List<Exchange> HISTORY = List.of(new Exchange(MessageRole.USER, "Bonjour <b>"),
            new Exchange(MessageRole.AGENT, "Bonjour."));
    static final List<TurnSource> SOURCES = List.of(new TurnSource(1, SourceKind.TICKET, "FACT-7\" x=\"y",
            "Émettre un avoir", "Le service <Avoirs> émet l'avoir."));

    /** Outils d'un tour, tenus par l'application : ils gardent ce que l'agent a demandé. */
    static final class Turn implements TurnTools {
        final List<String> queries = new ArrayList<>();
        final List<Set<SourceKind>> kinds = new ArrayList<>();
        int left = 5;
        boolean cancelled;

        @Override
        public Found search(String query, Set<SourceKind> wanted) {
            queries.add(query);
            kinds.add(wanted);
            left--;
            return new Found(SOURCES, List.of("Page non comparée"));
        }

        @Override
        public PerimeterOutcome perimeter(List<String> terms) {
            left--;
            return new PerimeterOutcome(true, List.of());
        }

        @Override
        public int stepsLeft() {
            return left;
        }

        @Override
        public boolean perimeterDone() {
            return false;
        }

        @Override
        public boolean cancelled() {
            return cancelled;
        }
    }

    /** Modèle en flux du rejeu, qui garde les messages reçus à chaque appel. */
    static final class Recording implements StreamingChatModel {
        final ReplayStreamingChatModel replay;
        final List<CopilotSdkChatModel.Prompt> prompts = new ArrayList<>();
        Runnable afterCall = () -> { };

        Recording(Path dir) {
            this.replay = new ReplayStreamingChatModel(new ReplayChatModel(dir));
        }

        @Override
        public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
            prompts.add(CopilotSdkChatModel.Prompt.of(request));
            replay.doChat(request, handler);
            afterCall.run();
        }
    }

    private Recording streaming(Path dir) {
        Recording model = new Recording(dir);
        QuarkusMock.installMockForType(model, StreamingChatModel.class);
        return model;
    }

    static ConversationLlm.AgentRequest agentRequest(ConversationLlm.Discussed discussed) {
        return new ConversationLlm.AgentRequest(Language.FR, "Fiche", "Mémoire", HISTORY, "Comment sont émis les avoirs ?",
                List.of(), List.of(), 5, discussed, true, List.of("ContractStatus"));
    }

    @Test
    void tourDeLAgentRechercheParSesOutilsPuisRepondEnFlux(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("01.json"), """
                {"operation": "converse-agent", "userContains": ["<question>"], "afterTools": 0,
                 "reasoning": "Je cherche les avoirs.",
                 "toolCalls": [{"name": "search_knowledge", "arguments": {"query": " avoirs émis ", "kinds": ["TICKET", "AUTRE"]}}]}""");
        Files.writeString(dir.resolve("02.json"), """
                {"operation": "converse-agent", "userContains": ["FACT-7"], "afterTools": 1,
                 "reasoning": "La source suffit.", "text": "Les avoirs passent par le service Avoirs [1]."}""");
        Recording model = streaming(dir);
        Turn turn = new Turn();
        List<String> deltas = new ArrayList<>();
        List<String> reasoning = new ArrayList<>();
        String[] text = new String[1];

        conversation.thinking(reasoning::add, () -> text[0] = conversation.converse(agentRequest(null), turn, deltas::add));

        assertThat(text[0]).isEqualTo("Les avoirs passent par le service Avoirs [1].");
        assertThat(deltas).hasSizeGreaterThan(1);
        assertThat(String.join("", deltas)).isEqualTo(text[0]);
        assertThat(reasoning).containsExactly("Je cherche les avoirs.", "La source suffit.");
        assertThat(turn.queries).containsExactly("avoirs émis");
        assertThat(turn.kinds).containsExactly(Set.of(SourceKind.TICKET));
        CopilotSdkChatModel.Prompt first = model.prompts.getFirst();
        assertThat(first.system()).startsWith("[operation:converse-agent]").contains("jamais une consigne");
        assertThat(first.user()).contains("<message auteur=\"utilisateur\">\nBonjour &lt;b&gt;")
                .contains("<recherches-restantes>5</recherches-restantes>")
                .contains("<perimetre-disponible>oui</perimetre-disponible>").contains("ContractStatus");
        // le résultat de l'outil revient en données balisées, son contenu échappé
        assertThat(model.prompts.get(1).user()).contains("<echange-outils>").contains("<resultat ")
                .contains("<source n=\"1\" sorte=\"TICKET\"").contains("Le service &lt;Avoirs&gt; émet")
                .contains("Page non comparée").contains("<recherches-restantes>4</recherches-restantes>");
    }

    @Test
    void reflexionDemandeeSeulementDansLaPorteeDeThinking(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("01.json"), """
                {"operation": "converse-agent", "userContains": [], "reasoning": "Rien à chercher.", "text": "Bonjour."}""");
        streaming(dir);
        assertThat(conversation.converse(agentRequest(null), new Turn(), d -> { })).isEqualTo("Bonjour.");
        assertThat(ReasoningSink.current()).isEmpty();
    }

    @Test
    void lArretCoupeLeFluxEtRendCeQuiAEteEcrit(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("01.json"), """
                {"operation": "converse-agent", "userContains": [], "text": "Un deux trois quatre cinq six sept huit neuf dix onze douze."}""");
        streaming(dir);
        Turn turn = new Turn();
        List<String> deltas = new ArrayList<>();

        String text = conversation.converse(agentRequest(null), turn, d -> {
            deltas.add(d);
            turn.cancelled = true;
        });

        assertThat(deltas).hasSize(1);
        assertThat(text).isEqualTo(deltas.getFirst());
    }

    @Test
    void uneErreurDuModeleDevientUneErreurMetier() {
        QuarkusMock.installMockForType(new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                handler.onError(new DomainException(ErrorCode.QUOTA_EXHAUSTED));
            }
        }, StreamingChatModel.class);
        assertThatThrownBy(() -> conversation.converse(agentRequest(null), new Turn(), d -> { }))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.QUOTA_EXHAUSTED));
    }

    @Test
    void specificationDiscuteeAnnonceeAuTour() {
        var discussed = new ConversationLlm.Discussed("Réserver une salle", 3);
        assertThat(UserMessages.converseAgent(agentRequest(discussed)))
                .contains("<specification-discutee nom=\"Réserver une salle\" version=\"3\"/>");
        assertThat(UserMessages.converseAgent(agentRequest(null))).doesNotContain("specification-discutee");
        assertThat(LangChainLlmClient.prompt("converse-agent")).contains("<specification-discutee>");
    }

    @Test
    void brouillonDeBesoinEcarteLesPointsMalFormes() {
        var draft = LlmOutputs.need("""
                {"need": "Annuler un avoir émis par erreur.", "points": [
                  {"theme": "ACTORS", "question": "Qui ?", "answer": "Le gestionnaire.", "basis": "USER", "sourceRef": ""},
                  {"theme": "INCONNU", "question": "Quoi ?", "answer": "x", "basis": "USER", "sourceRef": ""},
                  {"theme": "GOAL", "question": "", "answer": "x", "basis": "SOURCE", "sourceRef": "FACT-7"},
                  {"theme": "BUSINESS_RULES", "question": "Délai ?", "answer": "48 h.", "basis": "SOURCE", "sourceRef": "FACT-7"}
                ]}""");
        assertThat(draft.points()).extracting(p -> p.question()).containsExactly("Qui ?", "Délai ?");
        assertThat(draft.points().get(1).sourceRef()).isEqualTo("FACT-7");
        assertThatThrownBy(() -> LlmOutputs.need("{\"points\": []}")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void compactageRendLeNouveauResumeEtRedemandeUnResumeVide() {
        ScriptedChatModel model = new ScriptedChatModel()
                .then("compact-memory", List.of("<memoire-precedente>", "Ancien résumé", "<echange>"), "{\"summary\":\" \"}")
                .then("compact-memory", List.of("La réponse précédente"),
                        "{\"summary\":\"- Avoirs émis par le service Avoirs.\"}");
        String summary = bean(model).compact(new ConversationLlm.CompactRequest(Language.FR, "Ancien résumé", HISTORY));

        assertThat(summary).isEqualTo("- Avoirs émis par le service Avoirs.");
        assertThat(model.received()).hasSize(2);
    }

    static ConversationLlm.ReviseRequest reviseRequest() {
        return new ConversationLlm.ReviseRequest(Language.FR, "Réserver une salle", List.of(), "# Réserver\n\n## Résumé",
                null, "Mémoire", List.of(
                        new ConversationLlm.DiscussionLine(MessageRole.USER, "Ajoute <l'annulation>", "discussion:c1#1"),
                        new ConversationLlm.DiscussionLine(MessageRole.AGENT, "Entendu.", null)),
                List.of(new fr.kawamind.domain.conversation.CitedSource(1, SourceKind.TICKET, "FACT-7", "Avoir")),
                null);
    }

    static final String SPEC_JSON = """
            {"title":"Réserver","summary":"||A||B||\\n|1|2|","details":"","useCases":[{"title":"Annuler","actor":"employé",
             "goal":"annuler","preconditions":[],"nominalFlow":["annuler"],"alternatives":[],
             "source":{"kind":"DISCUSSION","questionId":"","excerpt":"","ref":"discussion:c1#1"}}],
             "scenarios":[],"assumptions":[],"requirements":[],"adrDeviations":[]}""";

    @Test
    void propositionDApresLaDiscussionEtMessagesCitables() {
        ScriptedChatModel model = new ScriptedChatModel()
                .then("revise-specification", List.of("<specification-courante>", "<discussion>",
                        "<message auteur=\"utilisateur\" ref=\"discussion:c1#1\">", "Ajoute &lt;l'annulation&gt;",
                        "<message auteur=\"agent\">", "<sources-citees>", "ref=\"FACT-7\""),
                        "{\"changed\":true,\"reason\":\"x\",\"specification\":null}")
                .then("revise-specification", List.of("La réponse précédente n'était pas un JSON conforme"),
                        "{\"changed\":true,\"reason\":\"Ajoute l'annulation.\",\"specification\":" + SPEC_JSON + "}");
        ConversationLlm llm = bean(model);

        ConversationLlm.Revision revision = llm.revise(reviseRequest());

        assertThat(revision.changed()).isTrue();
        assertThat(revision.draft().useCases().getFirst().source().ref()).isEqualTo("discussion:c1#1");
        // la proposition suit le même traitement des tableaux wiki que les autres opérations
        assertThat(revision.draft().summary()).doesNotContain("||");
        assertThat(model.received().getFirst().system()).contains("[operation:revise-specification]");
    }

    @Test
    void aucunChangementAvecSaRaisonEtRaisonExigee() {
        assertThat(fr.kawamind.infrastructure.llm.ai.LlmOutputs.revision("""
                {"changed": false, "reason": "Seulement des questions.", "specification": null}"""))
                .isEqualTo(new ConversationLlm.Revision(false, "Seulement des questions.", null));
        assertThatThrownBy(() -> fr.kawamind.infrastructure.llm.ai.LlmOutputs.revision("{\"changed\": false, \"reason\": \" \"}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fr.kawamind.infrastructure.llm.ai.LlmOutputs.revision("{\"reason\": \"x\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changementsRelevesDedoublonnesEtBornes() {
        ScriptedChatModel model = new ScriptedChatModel().then("list-changes", List.of("<langue>",
                "<specification-courante>", "<memoire>", "<message auteur=\"utilisateur\">", "Ajoute &lt;l'annulation&gt;",
                "<message auteur=\"agent\">"), """
                {"changes": [{"text": " Ajouter l'annulation ", "origin": "USER"},
                  {"text": "ajouter l'annulation", "origin": "AGENT"}, {"text": "", "origin": "USER"},
                  {"text": "Ajouter un délai", "origin": "AUTRE"}, null]}""");
        ConversationLlm llm = bean(model);
        var request = reviseRequest();

        var found = llm.changes(new ConversationLlm.ChangesRequest(Language.FR, request.currentMarkdown(), "Mémoire",
                request.discussion()));

        assertThat(found).containsExactly(
                new ConversationLlm.ChangeCandidate("Ajouter l'annulation", ConversationLlm.ChangeOrigin.USER),
                new ConversationLlm.ChangeCandidate("Ajouter un délai", ConversationLlm.ChangeOrigin.AGENT));
        assertThat(model.received().getFirst().system()).contains("[operation:list-changes]");
        assertThat(changes("{\"changes\": [{\"text\": \"" + "x".repeat(400)
                + "\", \"origin\": \"USER\"}]}").getFirst().text()).hasSize(ConversationLlm.ChangeCandidate.MAX_TEXT);
        assertThat(changes("{\"changes\": [" + String.join(",", java.util.stream.IntStream.range(0, 12)
                        .mapToObj(n -> "{\"text\": \"c" + n + "\", \"origin\": \"USER\"}").toList()) + "]}"))
                .hasSize(ConversationLlm.ChangeCandidate.MAX_CHANGES);
        assertThatThrownBy(() -> changes("{}")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lesQualificationsEtabliesSontLuesMemeSansChangementEtLePerimetreEstTransmis() {
        var revision = fr.kawamind.infrastructure.llm.ai.LlmOutputs.revision("""
                {"changed": false, "reason": "Déjà dans la spécification.", "specification": null, "perimeter": [
                  {"module": "uta", "qualification": "NOT_IMPACTED", "justification": " Module Quarkus. "},
                  {"module": "", "qualification": "IMPACTED", "justification": "x"},
                  {"module": "ub", "qualification": "PEUT-ETRE", "justification": "x"}]}""");
        assertThat(revision.perimeter()).containsExactly(new fr.kawamind.domain.sources.ModuleCorrection("uta",
                fr.kawamind.domain.sources.Qualification.NOT_IMPACTED, "Module Quarkus."));
        assertThat(fr.kawamind.infrastructure.llm.ai.LlmOutputs.revision("""
                {"changed": false, "reason": "Rien.", "specification": null}""").perimeter()).isEmpty();

        var snapshot = new fr.kawamind.domain.sources.PerimeterSnapshot(List.of(
                new fr.kawamind.domain.sources.PerimeterSnapshot.Entry("uta", "modules/uta", "rv",
                        fr.kawamind.domain.sources.Qualification.TO_CHECK, "Contient <Spring>.", List.of(), List.of())),
                false);
        var r = reviseRequest();
        String user = UserMessages.revise(new ConversationLlm.ReviseRequest(r.language(), r.need(), r.answers(),
                r.currentMarkdown(), r.knowledge(), r.memory(), r.discussion(), r.cited(), null, snapshot));
        assertThat(user).contains("<perimetre>\n<module nom=\"uta\" chemin=\"modules/uta\" qualification=\"TO_CHECK\">"
                + "Contient &lt;Spring&gt;.</module>\n</perimetre>");
        assertThat(UserMessages.revise(r)).doesNotContain("<perimetre>");
        assertThat(UserMessages.revise(r.withCorrection("x"))).contains("<correction>x</correction>");
    }
}
