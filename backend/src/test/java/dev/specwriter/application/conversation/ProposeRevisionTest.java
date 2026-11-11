package dev.specwriter.application.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.application.conversation.TurnEvents.Event;
import dev.specwriter.application.specification.AcceptAiEdit;
import dev.specwriter.application.specification.OperationRegistry;
import dev.specwriter.application.specification.ProposalStore;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.conversation.CitedSource;
import dev.specwriter.domain.conversation.ConversationMessage;
import dev.specwriter.domain.conversation.MessageRole;
import dev.specwriter.domain.conversation.MessageState;
import dev.specwriter.domain.conversation.SourceKind;
import dev.specwriter.domain.conversation.Thinking;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.llm.DraftSpecification;
import dev.specwriter.domain.llm.DraftSpecification.DraftScenario;
import dev.specwriter.domain.llm.DraftSpecification.DraftSource;
import dev.specwriter.domain.llm.DraftSpecification.DraftUseCase;
import dev.specwriter.domain.port.ConversationLlm.Revision;
import dev.specwriter.domain.port.ModelCapacities;
import dev.specwriter.domain.session.Implementation;
import dev.specwriter.domain.session.ImplementationStatus;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.specification.SourceRef;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.VersionOrigin;
import dev.specwriter.support.InMemoryConversations;
import dev.specwriter.support.InMemorySpecificationDiscussions;
import dev.specwriter.support.JiraWorld;
import dev.specwriter.support.ScriptedConversationLlm;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import dev.specwriter.domain.port.ConversationLlm.ChangeCandidate;
import dev.specwriter.domain.port.ConversationLlm.ChangeOrigin;
import dev.specwriter.domain.sources.ModuleCorrection;
import dev.specwriter.domain.sources.Qualification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 011, US2 : nouvelle version proposée d'après la discussion (research.md R4, R5). */
class ProposeRevisionTest {

    final JiraWorld w = new JiraWorld().grant(ConsentService.COPILOT);
    final InMemoryConversations conversations = new InMemoryConversations();
    final InMemorySpecificationDiscussions links = new InMemorySpecificationDiscussions();
    final ScriptedConversationLlm llm = new ScriptedConversationLlm();
    final OperationRegistry operations = new OperationRegistry();
    final TurnEvents events = new TurnEvents(w.clock);
    DiscussSpecification discussions;
    ProposeRevision propose;
    Session session;
    UUID cid;

    @BeforeEach
    void setUp() {
        discussions = new DiscussSpecification(w.sessions, w.versions, conversations, links, w.clock, w.ids);
        propose = new ProposeRevision(w.sessions, w.versions, conversations, discussions,
                dev.specwriter.application.knowledge.SessionKnowledge.sheetOnly(w.projects), llm,
                ModelCapacities.UNKNOWN, w.policy, operations, events, w.proposals, w.clock, w.ids, Runnable::run);
        session = w.generatedSession();
        cid = discussions.open(session.id()).conversationId();
    }

    void said(String question, String answer, CitedSource... cited) {
        int n = conversations.messages(cid).size();
        conversations.saveMessage(ConversationMessage.user(w.ids.next(), cid, n + 1, question, w.clock.now()));
        ConversationMessage agent = ConversationMessage.agentStarted(w.ids.next(), cid, n + 2, w.clock.now())
                .withText(answer).finished(MessageState.COMPLETE, false).about(1);
        conversations.saveMessage(agent);
        conversations.saveSources(agent.id(), List.of(cited));
    }

    static DraftSpecification revised(String ref) {
        return new DraftSpecification("Paiement en ligne", "Le client paie sa commande.", List.of(
                new DraftUseCase("Payer la commande", "client connecté", "Régler son panier", List.of(),
                        List.of("Il paie"), List.of(), new DraftSource("NEED_EXCERPT", "", "payer en ligne")),
                new DraftUseCase("Annuler la commande", "client connecté", "Annuler avant l'envoi", List.of(),
                        List.of("Il annule"), List.of(), new DraftSource("DISCUSSION", "", "", ref))),
                List.of(new DraftScenario(0, "Paiement", "NOMINAL", "client", List.of(), List.of("je paie"),
                                List.of("c'est payé"), false),
                        new DraftScenario(1, "Annulation", "NOMINAL", "client", List.of(), List.of("j'annule"),
                                List.of("c'est annulé"), false)),
                List.of(), List.of());
    }

    List<Event> run() {
        List<Event> seen = new ArrayList<>();
        UUID turn = propose.propose(cid).turnId();
        events.subscribe(turn, seen::add);
        return seen;
    }

    static void refused(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void proposeUneVersionTraceeSurLaDiscussionPuisLaVersionEnEstIssue() {
        said("Le client peut-il annuler ?", "La spécification ne le dit pas [1].",
                new CitedSource(1, SourceKind.TICKET, "FACT-7", "Émettre un avoir"));
        said("Oui, il annule tant que la commande n'est pas envoyée.", "C'est noté.");
        String ref = "discussion:" + cid + "#3";
        llm.reviseReasoning = "Je reprends la discussion.";
        llm.revisions.add(new Revision(true, "Ajoute l'annulation.", revised(ref)));

        List<Event> seen = run();

        assertThat(seen.getFirst()).isInstanceOf(Event.Started.class);
        assertThat(seen).contains(new Event.Stage(Thinking.Stage.REVISE), new Event.Reasoning("Je reprends la discussion."));
        Event.Done done = (Event.Done) seen.getLast();
        assertThat(done.unchanged()).isFalse();
        ProposalStore.Proposal p = w.proposals.find(session.id(), done.proposalId()).orElseThrow();
        assertThat(p.origin()).isEqualTo(VersionOrigin.DISCUSSION);
        assertThat(p.conversationId()).isEqualTo(cid);
        assertThat(p.baseVersion()).isEqualTo(1);
        SourceRef source = p.content().useCases().get(1).source();
        assertThat(source.ref()).isEqualTo(ref);
        assertThat(source.excerpt()).startsWith("Discussion du ").endsWith(", message 3");

        var request = llm.reviseRequests.getFirst();
        assertThat(request.currentMarkdown()).isEqualTo(w.versions.find(session.id(), 1).orElseThrow().markdown());
        assertThat(request.discussion()).extracting(l -> l.role(), l -> l.ref()).containsExactly(
                org.assertj.core.groups.Tuple.tuple(MessageRole.USER, "discussion:" + cid + "#1"),
                org.assertj.core.groups.Tuple.tuple(MessageRole.AGENT, null),
                org.assertj.core.groups.Tuple.tuple(MessageRole.USER, ref),
                org.assertj.core.groups.Tuple.tuple(MessageRole.AGENT, null));
        assertThat(request.cited()).extracting(CitedSource::ref).containsExactly("FACT-7");
        // la proposition n'est pas un message : rien n'est ajouté au fil
        assertThat(conversations.messages(cid)).hasSize(4);
        assertThat(operations.running(cid)).isFalse();
        assertThat(operations.running(session.id())).isFalse();

        SpecificationVersion v = new AcceptAiEdit(w.sessions, w.versions, w.tickets, w.proposals, w.clock, w.ids)
                .run(session.id(), p.id());
        assertThat(v.number()).isEqualTo(2);
        assertThat(w.versions.find(session.id(), 2).orElseThrow().origin()).isEqualTo(VersionOrigin.DISCUSSION);
        assertThat(w.versions.discussionOf(v.id())).contains(cid);
        assertThat(discussions.of(cid).orElseThrow().version()).isEqualTo(2);
    }

    @Test
    void uneSourceDeDiscussionDeLaVersionCouranteResteCitable() {
        said("Ajoute l'annulation.", "Entendu.");
        String earlier = "discussion:" + cid + "#1";
        llm.revisions.add(new Revision(true, "Ajoute l'annulation.", revised(earlier)));
        Event.Done first = (Event.Done) run().getLast();
        new AcceptAiEdit(w.sessions, w.versions, w.tickets, w.proposals, w.clock, w.ids).run(session.id(),
                first.proposalId());

        cid = discussions.fresh(session.id()).conversationId();
        said("Le titre devient « Paiement et annulation ».", "D'accord.");
        llm.revisions.clear();
        // l'annulation garde sa source : un message de la discussion précédente
        llm.revisions.add(new Revision(true, "Titre.", revised(earlier)));

        Event.Done done = (Event.Done) run().getLast();

        assertThat(done.proposalId()).isNotNull();
        assertThat(llm.reviseRequests.getLast().cited()).extracting(CitedSource::kind, CitedSource::ref)
                .contains(org.assertj.core.groups.Tuple.tuple(SourceKind.DISCUSSION, earlier));
    }

    @Test
    void aucunChangementEtabliEstDitSansProposition() {
        said("Que dit l'UC-01 ?", "Le client paie sa commande.");
        llm.revisions.add(new Revision(false, "La discussion n'a posé que des questions.", null));

        Event.Done done = (Event.Done) run().getLast();

        assertThat(done.unchanged()).isTrue();
        assertThat(done.reason()).isEqualTo("La discussion n'a posé que des questions.");
        assertThat(done.proposalId()).isNull();
    }

    @Test
    void uneSortieInvalideDonneUneErreurDansLeFlux() {
        said("Ajoute l'annulation.", "Entendu.");
        llm.revisions.add(new Revision(true, "x", revised("discussion:inconnue#9")));

        Event last = run().getLast();

        assertThat(last).isInstanceOfSatisfying(Event.Error.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.LLM_INVALID_OUTPUT.name()));
        assertThat(llm.reviseRequests).hasSize(3);
        assertThat(llm.reviseRequests.get(1).correction()).contains("DISCUSSION");
        assertThat(operations.running(cid)).isFalse();
    }

    @Test
    void refus() {
        refused(() -> propose.propose(cid), ErrorCode.VALIDATION_FAILED);
        said("Ajoute l'annulation.", "Entendu.");

        var ordinary = new dev.specwriter.domain.conversation.Conversation(w.ids.next(), session.projectId(), "Libre",
                w.clock.now(), w.clock.now());
        conversations.save(ordinary);
        refused(() -> propose.propose(ordinary.id()), ErrorCode.NOT_A_DISCUSSION);
        refused(() -> propose.propose(UUID.randomUUID()), ErrorCode.CONVERSATION_NOT_FOUND);

        try (var busy = operations.begin(cid)) {
            refused(() -> propose.propose(cid), ErrorCode.OPERATION_IN_PROGRESS);
        }

        w.consents.record(new dev.specwriter.domain.consent.ConsentRecord(ConsentService.COPILOT, false, Instant.EPOCH));
        refused(() -> propose.propose(cid), ErrorCode.CONSENT_REQUIRED);
        w.grant(ConsentService.COPILOT);

        w.sessions.saveImplementation(session.id(), new Implementation(ImplementationStatus.IMPLEMENTED,
                Instant.parse("2026-11-10T09:00:00Z"), null));
        refused(() -> propose.propose(cid), ErrorCode.SPECIFICATION_LOCKED);
    }

    @Test
    void lesChangementsAvancesSontReleves() {
        said("Le client peut-il annuler ?", "Proposons aussi un délai de rétractation.");
        llm.changes.add(List.of(new ChangeCandidate("Ajouter l'annulation", ChangeOrigin.USER),
                new ChangeCandidate("Ajouter un délai de rétractation", ChangeOrigin.AGENT)));

        assertThat(propose.changes(cid)).extracting(ChangeCandidate::origin)
                .containsExactly(ChangeOrigin.USER, ChangeOrigin.AGENT);

        var request = llm.changesRequests.getFirst();
        assertThat(request.currentMarkdown()).isEqualTo(w.versions.find(session.id(), 1).orElseThrow().markdown());
        assertThat(request.discussion()).extracting(l -> l.role()).containsExactly(MessageRole.USER, MessageRole.AGENT);
        assertThat(conversations.messages(cid)).hasSize(2);
        try (var busy = operations.begin(cid)) {
            refused(() -> propose.changes(cid), ErrorCode.OPERATION_IN_PROGRESS);
        }
    }

    @Test
    void lesChangementsRetenusEntrentDansLaDiscussionAvantLaRedaction() {
        said("Le client peut-il annuler ?", "Proposons un délai de rétractation.");
        llm.revisions.add(new Revision(true, "Ajoute l'annulation.", revised("discussion:" + cid + "#3")));

        ProposeRevision.Started started = propose.propose(cid, List.of(" Ajouter   l'annulation ", "",
                "Ajouter l'annulation", "Ajouter un délai de rétractation"));

        ConversationMessage m = started.message();
        assertThat(m.role()).isEqualTo(MessageRole.USER);
        assertThat(m.ordinal()).isEqualTo(3);
        assertThat(m.specificationVersion()).isEqualTo(1);
        assertThat(m.text()).isEqualTo("Changements retenus pour la nouvelle version :\n- Ajouter l'annulation\n"
                + "- Ajouter un délai de rétractation");
        assertThat(conversations.messages(cid)).last().isEqualTo(m);
        assertThat(llm.reviseRequests.getFirst().discussion().getLast().ref()).isEqualTo("discussion:" + cid + "#3");
        assertThat(propose.propose(cid, null).message()).isNull();
    }

    @Test
    void lesChangementsRetenusSontBornes() {
        said("Ajoute l'annulation.", "Entendu.");
        refused(() -> propose.propose(cid, List.of("x".repeat(ProposeRevision.MAX_CHANGE + 1))),
                ErrorCode.VALIDATION_FAILED);
        refused(() -> propose.propose(cid, java.util.stream.IntStream.rangeClosed(0, ProposeRevision.MAX_CHANGES)
                .mapToObj(n -> "Changement " + n).toList()), ErrorCode.VALIDATION_FAILED);
        assertThat(ProposeRevision.confirmation(List.of("Add"), dev.specwriter.domain.session.Language.EN))
                .isEqualTo("Changes to include in the new version:\n- Add");
        assertThat(conversations.messages(cid)).hasSize(2);
    }

    @Test
    void lesModulesDeLaDiscussionEtSesCorrectionsFontUneVersionMemeSansAutreChangement() {
        dev.specwriter.support.InMemoryPerimeters perimeters = new dev.specwriter.support.InMemoryPerimeters();
        ProposalStore store = new ProposalStore(w.diff, w.ids, w.clock, perimeters);
        propose = new ProposeRevision(w.sessions, w.versions, conversations, discussions,
                dev.specwriter.application.knowledge.SessionKnowledge.sheetOnly(w.projects), llm,
                ModelCapacities.UNKNOWN, w.policy, operations, events, store, w.clock, w.ids, Runnable::run, perimeters);
        said("Quels modules sont touchés ?", "a et b.");
        UUID answer = w.ids.next();
        perimeters.conversationOf.put(answer, cid);
        perimeters.save(answer, StartFromConversationTest.analysis(1, "libs/a", "modules/b"));
        said("b est en Quarkus, il n'est pas impacté.", "Entendu.");
        llm.revisions.add(new Revision(false, "Les use cases sont déjà à jour.", null,
                List.of(new ModuleCorrection("b", Qualification.NOT_IMPACTED, "Module Quarkus, hors de la migration."))));

        Event.Done done = (Event.Done) run().getLast();

        assertThat(done.unchanged()).isFalse();
        assertThat(llm.reviseRequests.getFirst().perimeter().modules()).extracting(e -> e.name())
                .containsExactly("a", "b");
        ProposalStore.Proposal p = store.find(session.id(), done.proposalId()).orElseThrow();
        // le contenu reste celui de la version courante ; le module à vérifier devient une hypothèse
        assertThat(p.content().useCases()).isEqualTo(w.versions.find(session.id(), 1).orElseThrow().content().useCases());
        assertThat(p.content().assumptions()).extracting(x -> x.statement()).anyMatch(t -> t.contains("Le module a"));
        assertThat(p.markdown()).contains("## Modules touchés").contains("### Non impactés")
                .contains("**b** (rv/modules/b) : Module Quarkus, hors de la migration.");
        new AcceptAiEdit(w.sessions, w.versions, w.tickets, store, w.clock, w.ids).run(session.id(), p.id());
        assertThat(perimeters.snapshot(session.id()).orElseThrow().modules().get(1).qualification())
                .isEqualTo(Qualification.NOT_IMPACTED);

        // le même périmètre, déjà dans la version : rien ne change
        llm.revisions.clear();
        llm.revisions.add(new Revision(false, "Rien de neuf.", null,
                List.of(new ModuleCorrection("b", Qualification.NOT_IMPACTED, "Module Quarkus, hors de la migration."))));
        Event.Done again = (Event.Done) run().getLast();
        assertThat(again.unchanged()).isTrue();
        assertThat(again.reason()).isEqualTo("Rien de neuf.");
    }
}
