package fr.kawamind.application.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.application.conversation.DiscussSpecification;
import fr.kawamind.application.conversation.ProposeRevision;
import fr.kawamind.application.conversation.TurnEvents;
import fr.kawamind.application.conversation.TurnEvents.Event;
import fr.kawamind.application.specification.AcceptAiEdit;
import fr.kawamind.application.specification.OperationRegistry;
import fr.kawamind.application.specification.ProposalStore;
import fr.kawamind.domain.consent.ConsentRecord;
import fr.kawamind.domain.consent.ConsentService;
import fr.kawamind.domain.conversation.ConversationMessage;
import fr.kawamind.domain.conversation.MessageRole;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.interview.InterviewPoint;
import fr.kawamind.domain.llm.DraftSpecification;
import fr.kawamind.domain.llm.DraftSpecification.DraftAssumption;
import fr.kawamind.domain.llm.DraftSpecification.DraftScenario;
import fr.kawamind.domain.llm.DraftSpecification.DraftSource;
import fr.kawamind.domain.llm.DraftSpecification.DraftUseCase;
import fr.kawamind.domain.port.ConversationLlm.Revision;
import fr.kawamind.domain.port.ModelCapacities;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.specification.Anchor;
import fr.kawamind.domain.specification.Assumption;
import fr.kawamind.domain.specification.MarkdownRenderer;
import fr.kawamind.domain.specification.OpenQuestion;
import fr.kawamind.domain.specification.SpecificationContent;
import fr.kawamind.domain.specification.SpecificationVersion;
import fr.kawamind.domain.specification.Specs;
import fr.kawamind.domain.specification.VersionOrigin;
import fr.kawamind.support.InMemoryConversations;
import fr.kawamind.support.InMemoryInterviews;
import fr.kawamind.support.InMemorySpecificationDiscussions;
import fr.kawamind.support.JiraWorld;
import fr.kawamind.support.ScriptedConversationLlm;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 021, US3 : la révision d'une spécification d'après la séance (research.md R3, R4, R11, R12). */
class InterviewRevisionTest {

    final JiraWorld w = new JiraWorld().grant(ConsentService.COPILOT);
    final InMemoryConversations conversations = new InMemoryConversations();
    final InMemorySpecificationDiscussions links = new InMemorySpecificationDiscussions();
    final InMemoryInterviews interviews = new InMemoryInterviews();
    final ScriptedConversationLlm llm = new ScriptedConversationLlm();
    final OperationRegistry operations = new OperationRegistry();
    final TurnEvents events = new TurnEvents(w.clock);
    RunInterview run;
    DiscussSpecification discussions;
    InterviewRevision revision;
    Session session;
    UUID interview;

    @BeforeEach
    void setUp() {
        discussions = new DiscussSpecification(w.sessions, w.versions, conversations, links, w.clock, w.ids);
        ProposeRevision propose = new ProposeRevision(w.sessions, w.versions, conversations, discussions,
                fr.kawamind.application.knowledge.SessionKnowledge.sheetOnly(w.projects), llm, ModelCapacities.UNKNOWN,
                w.policy, operations, events, w.proposals, w.clock, w.ids, Runnable::run, null, interviews);
        revision = new InterviewRevision(interviews, w.sessions, w.versions, conversations, discussions, propose,
                w.policy, w.clock, w.ids);
        run = new RunInterview(interviews, w.sessions, w.versions, operations, w.clock, w.ids);
        session = w.generatedSession();
        version(2, Specs.valid().withAssumptions(List.of(
                new Assumption("H-01", "Le montant est TTC.", Assumption.Origin.TOOL_INFERENCE, null,
                        new Anchor("UC-01", 2, false), null),
                new Assumption("H-02", "Le client est prévenu par courriel.", Assumption.Origin.TOOL_INFERENCE, null))));
        interview = run.startForSpecification(session.id(), List.of("Julie M.", "Paul D.")).interview().id();
    }

    SpecificationVersion version(int number, SpecificationContent content) {
        SpecificationVersion v = new SpecificationVersion(w.ids.next(), session.id(), number, content, "fp" + number,
                VersionOrigin.DISCUSSION, MarkdownRenderer.render(content, session.language()), w.clock.now());
        w.versions.save(v);
        return v;
    }

    void point(InterviewPoint.Origin origin, String code, Anchor anchor, String text, InterviewPoint.Outcome outcome,
            String owner) {
        run.savePoint(interview, null, new RunInterview.PointInput(session.id(), anchor, origin, code, text, outcome, owner,
                null, null));
    }

    void decided() {
        point(InterviewPoint.Origin.ASSUMPTION, "H-01", new Anchor("UC-01", 2, false), "", InterviewPoint.Outcome.CONFIRMED,
                null);
        point(InterviewPoint.Origin.QUESTION, null, new Anchor("UC-01", 1, false), "Qui valide en l'absence du manager ?",
                InterviewPoint.Outcome.DEFERRED, "Paul D.");
        run.finish(interview);
    }

    static DraftSpecification draft(String ref) {
        return new DraftSpecification("Paiement en ligne", "Le client paie sa commande.", List.of(
                new DraftUseCase("Payer la commande", "client connecté", "Régler son panier", List.of(),
                        List.of("Le client valide le panier", "Il paie, montant TTC"), List.of(),
                        new DraftSource("DISCUSSION", "", "", ref))),
                List.of(new DraftScenario(0, "Paiement", "NOMINAL", "client", List.of(), List.of("je paie"),
                        List.of("c'est payé"), false)),
                List.of(new DraftAssumption("Le montant est TTC.", "TOOL_INFERENCE", "", "H-01"),
                        new DraftAssumption("Le client est prévenu par courriel.", "TOOL_INFERENCE", "", "H-02")),
                List.of());
    }

    ProposalStore.Proposal proposed(InterviewRevision.Started started) {
        List<Event> seen = new ArrayList<>();
        events.subscribe(started.turnId(), seen::add);
        Event.Done done = (Event.Done) seen.getLast();
        return w.proposals.find(session.id(), done.proposalId()).orElseThrow();
    }

    static void refused(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void unSeulMessageDeSeanceDansLaDiscussionCreeePourLOccasion() {
        decided();
        assertThat(discussions.current(session.id())).isEmpty();
        llm.revisions.add(new Revision(true, "Intègre la séance.", draft("discussion:x#1")));

        InterviewRevision.Started started = revision.propose(interview, session.id());

        assertThat(discussions.current(session.id())).hasValueSatisfying(
                d -> assertThat(d.conversationId()).isEqualTo(started.conversationId()));
        List<ConversationMessage> messages = conversations.messages(started.conversationId());
        assertThat(messages).singleElement().satisfies(m -> {
            assertThat(m.role()).isEqualTo(MessageRole.USER);
            assertThat(m.text()).startsWith("Séance du ").contains(" avec Julie M., Paul D. Décisions à intégrer :")
                    .contains("\n- H-01 (UC-01, étape 2) — confirmé : Le montant est TTC.")
                    .contains("Questions reportées (ajoutées comme points ouverts, ne pas les reformuler) :\n"
                            + "- UC-01, étape 1 — Qui valide en l'absence du manager ? (Paul D.)");
            assertThat(interviews.interviewOfMessage(m.id())).contains(interview);
        });

        // relancée tant qu'elle n'est pas appliquée : pas de second message
        revision.propose(interview, session.id());
        assertThat(conversations.messages(started.conversationId())).hasSize(1);
    }

    @Test
    void laPropositionAppliqueLIssueDeLaSeanceEtCiteLaSeance() {
        decided();
        UUID cid = discussions.open(session.id()).conversationId();
        llm.revisions.add(new Revision(true, "Intègre la séance.", draft("discussion:" + cid + "#1")));

        ProposalStore.Proposal p = proposed(revision.propose(interview, session.id()));

        assertThat(p.interviewId()).isEqualTo(interview);
        assertThat(p.content().assumptions()).extracting(Assumption::code, Assumption::statement).containsExactly(
                org.assertj.core.groups.Tuple.tuple("H-01", "Le client est prévenu par courriel."));
        assertThat(p.content().openQuestions()).containsExactly(new OpenQuestion("Q-01",
                "Qui valide en l'absence du manager ?", "Paul D.", null, new Anchor("UC-01", 1, false), null));
        assertThat(p.content().useCases().getFirst().source().excerpt()).startsWith("Séance du ")
                .endsWith(" (Julie M., Paul D.)");
        assertThat(p.markdown()).contains("## Points ouverts");
    }

    @Test
    void laLimiteDeVingtChangementsNeSAppliquePas() {
        for (int i = 0; i < 25; i++) {
            point(InterviewPoint.Origin.REMARK, null, new Anchor("UC-01", 1, false), "Changement " + i,
                    InterviewPoint.Outcome.CHANGE, null);
        }
        run.finish(interview);
        llm.revisions.add(new Revision(true, "Intègre la séance.", draft("discussion:x#1")));
        InterviewRevision.Started started = revision.propose(interview, session.id());
        assertThat(conversations.messages(started.conversationId()).getFirst().text()).contains("Changement 24");
    }

    @Test
    void sansAccordCopilotLaRaisonEstDonneeEtRienNEstAjoute() {
        decided();
        w.consents.record(new ConsentRecord(ConsentService.COPILOT, false, Instant.EPOCH.plusSeconds(60)));
        var view = run.view(interview);
        assertThat(revision.unavailable(view.interview(), session.id(), view.points(), null))
                .hasValueSatisfying(r -> assertThat(r).contains("Configuration"));
        refused(() -> revision.propose(interview, session.id()), ErrorCode.CONSENT_REQUIRED);
        assertThat(discussions.current(session.id())).isEmpty();
    }

    @Test
    void uneSeanceEnCoursNeSeRevisePas() {
        point(InterviewPoint.Origin.ASSUMPTION, "H-01", null, "", InterviewPoint.Outcome.CONFIRMED, null);
        refused(() -> revision.propose(interview, session.id()), ErrorCode.INTERVIEW_NOT_FINISHED);
    }

    @Test
    void laRevisionPartDeLaDerniereVersion() {
        decided();
        SpecificationVersion v3 = version(3, Specs.valid());
        llm.revisions.add(new Revision(true, "Intègre la séance.", draft("discussion:x#1")));
        revision.propose(interview, session.id());
        assertThat(llm.reviseRequests.getFirst().currentMarkdown()).isEqualTo(v3.markdown());
        // l'énoncé de l'hypothèse vient de la version relue, qui l'avait encore
        assertThat(conversations.messages(discussions.current(session.id()).orElseThrow().conversationId())
                .getFirst().text()).contains("— confirmé : Le montant est TTC.");
    }

    @Test
    void appliqueeLaVersionEstNoteeEtLaRevisionNeSeReproposePlus() {
        decided();
        UUID cid = discussions.open(session.id()).conversationId();
        llm.revisions.add(new Revision(true, "Intègre la séance.", draft("discussion:" + cid + "#1")));
        ProposalStore.Proposal p = proposed(revision.propose(interview, session.id()));

        SpecificationVersion applied = new AcceptAiEdit(w.sessions, w.versions, w.tickets, w.proposals, w.clock, w.ids,
                interviews).run(session.id(), p.id());

        assertThat(interviews.specs(interview).getFirst().appliedVersion()).isEqualTo(applied.number()).isEqualTo(3);
        var view = run.view(interview);
        assertThat(revision.unavailable(view.interview(), session.id(), view.points(), 3)).contains("Version 3 appliquée.");
        refused(() -> revision.propose(interview, session.id()), ErrorCode.VALIDATION_FAILED);
    }
}
