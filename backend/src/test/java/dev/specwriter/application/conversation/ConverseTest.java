package dev.specwriter.application.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.application.conversation.TurnEvents.Event;
import dev.specwriter.application.knowledge.AnalyzeArchitecture;
import dev.specwriter.application.knowledge.SelectKnowledge;
import dev.specwriter.application.specification.OperationRegistry;
import dev.specwriter.domain.attachment.Attachment;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.conversation.CitedSource;
import dev.specwriter.domain.conversation.Conversation;
import dev.specwriter.domain.conversation.ConversationMessage;
import dev.specwriter.domain.conversation.MessageRole;
import dev.specwriter.domain.conversation.MessageState;
import dev.specwriter.domain.conversation.SearchStep;
import dev.specwriter.domain.conversation.SourceKind;
import dev.specwriter.domain.conversation.Thinking;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.port.ConversationLlm.Action;
import dev.specwriter.domain.port.ConversationLlm.Plan;
import dev.specwriter.domain.port.ModelCapacities;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectContext;
import dev.specwriter.support.FakeEmbedding;
import dev.specwriter.support.FakeLocalSearch;
import dev.specwriter.support.InMemoryConversationAttachments;
import dev.specwriter.support.InMemoryConversations;
import dev.specwriter.support.InMemoryKnowledgeIndex;
import dev.specwriter.support.JiraWorld;
import dev.specwriter.support.ScriptedConversationLlm;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 010, US1 : boucle de l'agent (research.md R1, R2, R4). */
class ConverseTest {

    static final UUID P = Project.DEFAULT_ID;
    static final Instant T = Instant.parse("2026-11-09T09:00:00Z");

    final JiraWorld w = new JiraWorld().grant(ConsentService.COPILOT);
    final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    final InMemoryConversations conversations = new InMemoryConversations();
    final ScriptedConversationLlm llm = new ScriptedConversationLlm();
    final OperationRegistry operations = new OperationRegistry();
    final TurnEvents events = new TurnEvents(w.clock);
    final List<Runnable> queued = new ArrayList<>();
    Converse converse;
    Converse deferred;
    Conversation conversation;

    @BeforeEach
    void setUp() {
        ConversationSearch search = new ConversationSearch(index, new FakeEmbedding(), new FakeLocalSearch());
        converse = new Converse(w.projects, conversations, llm, search, ModelCapacities.UNKNOWN, w.policy, operations,
                events, w.clock, w.ids, Runnable::run);
        deferred = new Converse(w.projects, conversations, llm, search, ModelCapacities.UNKNOWN, w.policy, operations,
                events, w.clock, w.ids, queued::add);
        w.projects.save(w.projects.find(P).orElseThrow().withContext(ProjectContext.edited("Aurora gère les avoirs.", T)));
        index.save(KnowledgeItem.ticket(P, "FACT-7", "Émettre un avoir", "L'avoir est émis par le service Avoirs.",
                "Terminé", "done", "Story", T, T), null, null);
        conversation = converse.create(P);
    }

    List<Event> subscribe(UUID turn) {
        List<Event> seen = new ArrayList<>();
        events.subscribe(turn, seen::add);
        return seen;
    }

    static void refused(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void chercheCiteEtRepondEnFlux() {
        llm.plans.add(new Plan(Action.SEARCH, "émission des avoirs", Set.of(SourceKind.TICKET)));
        llm.answers.add(List.of("L'avoir est émis ", "par le service Avoirs [1]. ", "Voir [7]."));

        Converse.Started started = converse.send(conversation.id(), "Comment sont émis les avoirs ?");

        List<ConversationMessage> messages = conversations.messages(conversation.id());
        assertThat(messages).extracting(ConversationMessage::role).containsExactly(MessageRole.USER, MessageRole.AGENT);
        ConversationMessage answer = messages.get(1);
        assertThat(answer.id()).isEqualTo(started.turnId());
        assertThat(answer.state()).isEqualTo(MessageState.COMPLETE);
        assertThat(answer.text()).isEqualTo("L'avoir est émis par le service Avoirs [1]. Voir.");
        assertThat(conversations.sources(answer.id())).containsExactly(
                new CitedSource(1, SourceKind.TICKET, "FACT-7", "Émettre un avoir"));
        assertThat(conversations.steps(answer.id())).containsExactly(
                new SearchStep(1, "émission des avoirs", Set.of(SourceKind.TICKET), 1));

        // le premier plan ne sait rien encore ; le second voit la source trouvée
        assertThat(llm.planRequests).hasSize(2);
        assertThat(llm.planRequests.getFirst().searchesLeft()).isEqualTo(5);
        assertThat(llm.planRequests.getFirst().generalSheet()).isEqualTo("Aurora gère les avoirs.");
        assertThat(llm.planRequests.get(1).found()).extracting(s -> s.ref()).containsExactly("FACT-7");
        assertThat(llm.answerRequests.getFirst().searchStopped()).isFalse();
        assertThat(llm.answerRequests.getFirst().question()).isEqualTo("Comment sont émis les avoirs ?");

        // tout est rejoué à l'écran qui s'abonne après coup
        List<Event> seen = subscribe(started.turnId());
        assertThat(seen.getFirst()).isEqualTo(new Event.Started(w.clock.now()));
        assertThat(seen).filteredOn(e -> e instanceof Event.Step).hasSize(1);
        assertThat(seen).filteredOn(e -> e instanceof Event.Delta).hasSize(3);
        assertThat(seen.get(seen.size() - 2)).isInstanceOf(Event.Sources.class);
        assertThat(seen.getLast()).isEqualTo(new Event.Done(MessageState.COMPLETE, false, 0));
        // le nom de la conversation vient du premier message
        assertThat(conversations.find(conversation.id()).orElseThrow().name()).isEqualTo("Comment sont émis les avoirs ?");
    }

    @Test
    void reflexionPublieeParEtapeEtGardeeAvecLaDuree() {
        llm.plans.add(new Plan(Action.SEARCH, "émission des avoirs", Set.of()));
        llm.planReasoning = "Je cherche qui émet.";
        llm.answerReasoning = "La source 1 répond.";
        llm.answers.add(List.of("Le service Avoirs [1]."));

        Converse.Started started = converse.send(conversation.id(), "Qui émet les avoirs ?");

        List<Event> seen = subscribe(started.turnId());
        assertThat(seen).filteredOn(e -> e instanceof Event.Stage || e instanceof Event.Reasoning).containsExactly(
                new Event.Stage(Thinking.Stage.PLAN), new Event.Reasoning("Je cherche qui émet."),
                new Event.Stage(Thinking.Stage.PLAN), new Event.Reasoning("Je cherche qui émet."),
                new Event.Stage(Thinking.Stage.ANSWER), new Event.Reasoning("La source 1 répond."));
        Thinking kept = conversations.thinking(started.turnId()).orElseThrow();
        assertThat(kept.parts()).containsExactly(new Thinking.Part(Thinking.Stage.PLAN, "Je cherche qui émet."),
                new Thinking.Part(Thinking.Stage.PLAN, "Je cherche qui émet."),
                new Thinking.Part(Thinking.Stage.ANSWER, "La source 1 répond."));
        assertThat(kept.durationMs()).isZero();
        assertThat(kept.toString()).doesNotContain("Je cherche");
    }

    @Test
    void reflexionBorneeEtGardeeQuandLeTourEchoue() {
        llm.planReasoning = "x".repeat(Thinking.MAX_TEXT + 10);
        llm.answers.add(List.of("Début ", "fin"));
        llm.failure = new DomainException(ErrorCode.LLM_UNAVAILABLE);
        llm.failAfter = 1;

        Converse.Started started = converse.send(conversation.id(), "Qui émet les avoirs ?");

        List<Event> seen = subscribe(started.turnId());
        assertThat(seen).filteredOn(e -> e instanceof Event.Reasoning)
                .extracting(e -> ((Event.Reasoning) e).text().length()).containsExactly(Thinking.MAX_TEXT);
        Thinking kept = conversations.thinking(started.turnId()).orElseThrow();
        assertThat(kept.length()).isEqualTo(Thinking.MAX_TEXT);
        assertThat(kept.parts()).extracting(Thinking.Part::stage).containsExactly(Thinking.Stage.PLAN);
        assertThat(seen.getLast()).isInstanceOf(Event.Error.class);
    }

    @Test
    void documentsJointsEtDesignesEnTeteDesSources() {
        InMemoryConversationAttachments joined = new InMemoryConversationAttachments();
        joined.add(Attachment.create(UUID.randomUUID(), conversation.id(), 1, "Règles de lettrage", "lettrage.pdf",
                "Le lettrage se fait sous 48 heures.", T));
        SelectKnowledge select = new SelectKnowledge(w.projects, index, new FakeEmbedding(),
                new AnalyzeArchitecture(index, w.llm, w.policy));
        Converse withDocuments = new Converse(w.projects, conversations, llm,
                new ConversationSearch(index, new FakeEmbedding(), new FakeLocalSearch()),
                new DesignatedDocuments(select, joined), ModelCapacities.UNKNOWN, w.policy, operations, events, w.clock,
                w.ids, Runnable::run);
        llm.plans.add(new Plan(Action.SEARCH, "lettrage", Set.of()));
        llm.answers.add(List.of("Sous 48 heures [1]."));

        withDocuments.send(conversation.id(), "Le délai de lettrage est-il cohérent avec les tickets ?");

        assertThat(llm.planRequests.getFirst().found()).extracting(s -> s.kind(), s -> s.ref())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(SourceKind.ATTACHMENT, "joint:lettrage.pdf"));
        assertThat(conversations.sources(conversations.messages(conversation.id()).getLast().id()))
                .containsExactly(new CitedSource(1, SourceKind.ATTACHMENT, "joint:lettrage.pdf", "Règles de lettrage"));
    }

    @Test
    void discussionLaSpecificationParSectionsEnTeteEtSaVersionGardee() {
        dev.specwriter.domain.session.Session session = w.generatedSession();
        dev.specwriter.support.InMemorySpecificationDiscussions links = new dev.specwriter.support.InMemorySpecificationDiscussions();
        DiscussSpecification discussions = new DiscussSpecification(w.sessions, w.versions, conversations, links, w.clock,
                w.ids);
        FakeLocalSearch specs = new FakeLocalSearch();
        specs.specs.add(new dev.specwriter.domain.port.LocalSearch.SpecHit(session.id(), 1, session.name(),
                "Extrait de la spécification discutée", false));
        UUID other = UUID.randomUUID();
        specs.specs.add(new dev.specwriter.domain.port.LocalSearch.SpecHit(other, 2, "Autre spécification",
                "Les avoirs sont émis par le service Avoirs.", false));
        Converse discussing = new Converse(w.projects, conversations, llm,
                new ConversationSearch(index, new FakeEmbedding(), specs), DesignatedDocuments.NONE,
                ModelCapacities.UNKNOWN, w.policy, operations, events, w.clock, w.ids, Runnable::run, discussions);
        UUID cid = discussions.open(session.id()).conversationId();
        llm.plans.add(new Plan(Action.SEARCH, "paiement", Set.of(SourceKind.SPECIFICATION)));
        llm.answers.add(List.of("UC-01 [4], et l'autre spécification [8]."));

        Converse.Started started = discussing.send(cid, "Que dit la spécification du paiement ?");

        var first = llm.planRequests.getFirst();
        assertThat(first.discussed()).isEqualTo(new dev.specwriter.domain.port.ConversationLlm.Discussed(session.name(), 1));
        assertThat(first.found()).extracting(s -> s.ref()).startsWith("spec:" + session.id() + "@v1#introduction",
                "spec:" + session.id() + "@v1#summary", "spec:" + session.id() + "@v1#use-cases",
                "spec:" + session.id() + "@v1#UC-01");
        assertThat(first.found()).allSatisfy(s -> assertThat(s.kind()).isEqualTo(SourceKind.SPECIFICATION));
        // la recherche n'ajoute pas d'extrait de la spécification discutée, mais trouve les autres
        var answered = llm.answerRequests.getFirst();
        assertThat(answered.discussed()).isNotNull();
        assertThat(answered.sources()).extracting(s -> s.ref()).noneMatch(r -> r.equals("spec:" + session.id() + "@v1"))
                .contains("spec:" + other + "@v2");
        ConversationMessage answer = conversations.messages(cid).getLast();
        assertThat(answer.specificationVersion()).isEqualTo(1);
        assertThat(conversations.sources(started.turnId())).extracting(CitedSource::ref)
                .contains("spec:" + session.id() + "@v1#UC-01");
    }

    @Test
    void discussionRefuseeSurUneSpecificationImplementeeEtOrdinaireUneFoisDetachee() {
        dev.specwriter.domain.session.Session session = w.generatedSession();
        dev.specwriter.support.InMemorySpecificationDiscussions links = new dev.specwriter.support.InMemorySpecificationDiscussions();
        DiscussSpecification discussions = new DiscussSpecification(w.sessions, w.versions, conversations, links, w.clock,
                w.ids);
        Converse discussing = new Converse(w.projects, conversations, llm,
                new ConversationSearch(index, new FakeEmbedding(), new FakeLocalSearch()), DesignatedDocuments.NONE,
                ModelCapacities.UNKNOWN, w.policy, operations, events, w.clock, w.ids, Runnable::run, discussions);
        UUID cid = discussions.open(session.id()).conversationId();
        w.sessions.saveImplementation(session.id(), new dev.specwriter.domain.session.Implementation(
                dev.specwriter.domain.session.ImplementationStatus.IMPLEMENTED, T, null));

        refused(() -> discussing.send(cid, "Et maintenant ?"), ErrorCode.SPECIFICATION_LOCKED);

        links.detachAll(session.id());
        discussing.send(cid, "Et maintenant ?");
        assertThat(llm.planRequests.getLast().discussed()).isNull();
        assertThat(conversations.messages(cid).getLast().specificationVersion()).isNull();
    }

    @Test
    void sourcesHorsPlaceDitesNonComparees() {
        Converse.Sources sources = new Converse.Sources(60);
        sources.add(List.of(new ConversationSearch.Found(SourceKind.TICKET, "A-1", "Court", "Vingt caractères ici."),
                new ConversationSearch.Found(SourceKind.TICKET, "A-2", "Long", "x".repeat(100)),
                new ConversationSearch.Found(SourceKind.TICKET, "A-3", "Bref", "Ok.")));
        assertThat(sources.all()).extracting(s -> s.ref()).containsExactly("A-1", "A-3");
        assertThat(sources.notCompared()).containsExactly("Long");
    }

    @Test
    void cinqRecherchesAuPlus() {
        for (int i = 0; i < 8; i++) {
            llm.plans.add(new Plan(Action.SEARCH, "avoirs " + i, Set.of()));
        }
        Converse.Started started = converse.send(conversation.id(), "Tout sur les avoirs, partout ?");

        assertThat(llm.planRequests).hasSize(5);
        assertThat(llm.planRequests).extracting(r -> r.searchesLeft()).containsExactly(5, 4, 3, 2, 1);
        assertThat(conversations.steps(started.turnId())).hasSize(5);
        assertThat(llm.answerRequests.getFirst().searchStopped()).isTrue();
        // une source trouvée deux fois garde son numéro
        assertThat(llm.answerRequests.getFirst().sources()).extracting(s -> s.n()).containsExactly(1);
    }

    @Test
    void lHistoriqueDebordantEstResumeEtLeCompactageSuivi() {
        llm.answers.add(List.of("Première réponse."));
        converse.send(conversation.id(), "Première question sur les avoirs ?");
        llm.answers.add(List.of("Deuxième réponse."));
        converse.send(conversation.id(), "Et qui consomme l'événement ?");

        assertThat(llm.answerRequests.get(1).history()).extracting(e -> e.text())
                .containsExactly("Première question sur les avoirs ?", "Première réponse.");
        assertThat(conversations.messages(conversation.id()).getLast().historyTruncated()).isFalse();

        llm.answers.add(List.of("x".repeat(9_000)));
        converse.send(conversation.id(), "Une réponse très longue ?");
        Converse.Started started = converse.send(conversation.id(), "Et après ?");

        // la réponse de 9 000 caractères déborde : les échanges anciens sont résumés, le résumé transmis
        assertThat(llm.compactRequests).isNotEmpty();
        assertThat(llm.answerRequests.getLast().memory()).startsWith("Résumé");
        assertThat(conversations.messages(conversation.id()).getLast().historyTruncated()).isFalse();
        // l'écran suit le compactage pendant le tour
        assertThat(subscribe(started.turnId())).filteredOn(e -> e instanceof Event.Memory)
                .containsExactly(new Event.Memory(ConversationHistory.Phase.STARTED),
                        new Event.Memory(ConversationHistory.Phase.DONE));
    }

    @Test
    void compactageEnEchecLaReponseContinueSignalee() {
        llm.answers.add(List.of("x".repeat(9_000)));
        converse.send(conversation.id(), "Une réponse très longue ?");
        llm.compactFailure = new DomainException(ErrorCode.LLM_UNAVAILABLE);
        Converse.Started started = converse.send(conversation.id(), "Et après ?");

        ConversationMessage answer = conversations.messages(conversation.id()).getLast();
        assertThat(answer.state()).isEqualTo(MessageState.COMPLETE);
        assertThat(answer.historyTruncated()).isTrue();
        assertThat(subscribe(started.turnId())).filteredOn(e -> e instanceof Event.Memory)
                .containsExactly(new Event.Memory(ConversationHistory.Phase.STARTED),
                        new Event.Memory(ConversationHistory.Phase.FAILED));
    }

    @Test
    void arretGardeLeTexteEcrit() {
        llm.answers.add(List.of("Début ", "de la réponse ", "jamais écrite."));
        Converse.Started[] started = new Converse.Started[1];
        llm.afterChunk = () -> {
            if (llm.answerRequests.size() == 1 && started[0] == null) {
                started[0] = new Converse.Started(null, conversations.messages(conversation.id()).getLast().id());
                converse.stop(conversation.id(), started[0].turnId());
            }
        };
        converse.send(conversation.id(), "Question qui sera arrêtée ?");

        ConversationMessage answer = conversations.messages(conversation.id()).getLast();
        assertThat(answer.state()).isEqualTo(MessageState.INCOMPLETE);
        assertThat(answer.text()).isEqualTo("Début ");
        assertThat(subscribe(answer.id()).getLast()).isEqualTo(new Event.Done(MessageState.INCOMPLETE, false, 0));
    }

    @Test
    void panneEtReessai() {
        llm.answers.add(List.of("Début ", "jamais"));
        llm.failure = new DomainException(ErrorCode.LLM_UNAVAILABLE);
        llm.failAfter = 1;
        Converse.Started first = converse.send(conversation.id(), "Question pendant une panne ?");

        ConversationMessage failed = conversations.messages(conversation.id()).getLast();
        assertThat(failed.state()).isEqualTo(MessageState.INCOMPLETE);
        assertThat(failed.text()).isEqualTo("Début ");
        assertThat(subscribe(first.turnId()).getLast()).isInstanceOfSatisfying(Event.Error.class,
                e -> assertThat(e.code()).isEqualTo("LLM_UNAVAILABLE"));

        llm.answers.add(List.of("Réponse complète."));
        Converse.Started retried = converse.retry(conversation.id(), first.userMessageId());
        assertThat(retried.turnId()).isEqualTo(first.turnId());
        assertThat(conversations.messages(conversation.id())).hasSize(2).last()
                .satisfies(m -> {
                    assertThat(m.state()).isEqualTo(MessageState.COMPLETE);
                    assertThat(m.text()).isEqualTo("Réponse complète.");
                });
        // seul un message dont la réponse est incomplète se réessaie
        refused(() -> converse.retry(conversation.id(), first.userMessageId()), ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void refus() {
        refused(() -> converse.send(conversation.id(), "x".repeat(8_001)), ErrorCode.MESSAGE_TOO_LONG);
        refused(() -> converse.send(UUID.randomUUID(), "Bonjour ?"), ErrorCode.CONVERSATION_NOT_FOUND);
        // un seul tour à la fois
        deferred.send(conversation.id(), "Première question ?");
        refused(() -> deferred.send(conversation.id(), "Deuxième ?"), ErrorCode.OPERATION_IN_PROGRESS);
        queued.forEach(Runnable::run);
        assertThat(operations.running(conversation.id())).isFalse();
        // sans accord, rien n'est enregistré ni envoyé
        w.consents.record(new dev.specwriter.domain.consent.ConsentRecord(ConsentService.COPILOT, false, T));
        int before = conversations.messages(conversation.id()).size();
        refused(() -> converse.send(conversation.id(), "Encore une question ?"), ErrorCode.CONSENT_REQUIRED);
        assertThat(conversations.messages(conversation.id())).hasSize(before);
    }
}
