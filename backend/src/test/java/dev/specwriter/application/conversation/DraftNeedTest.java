package dev.specwriter.application.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.consent.ConsentRecord;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.conversation.CitedSource;
import dev.specwriter.domain.conversation.Conversation;
import dev.specwriter.domain.conversation.ConversationMessage;
import dev.specwriter.domain.conversation.EstablishedPoint;
import dev.specwriter.domain.conversation.EstablishedPoint.Basis;
import dev.specwriter.domain.conversation.MessageState;
import dev.specwriter.domain.conversation.NeedDraft;
import dev.specwriter.domain.conversation.SourceKind;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ModelCapacities;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.question.QuestionTheme;
import dev.specwriter.support.InMemoryConversations;
import dev.specwriter.support.JiraWorld;
import dev.specwriter.support.ScriptedConversationLlm;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 010, US2 : brouillon de besoin tiré d'une conversation. */
class DraftNeedTest {

    static final Instant T = Instant.parse("2026-11-09T09:00:00Z");

    final JiraWorld w = new JiraWorld().grant(ConsentService.COPILOT);
    final InMemoryConversations conversations = new InMemoryConversations();
    final ScriptedConversationLlm llm = new ScriptedConversationLlm();
    DraftNeed draftNeed;
    Conversation conversation;

    @BeforeEach
    void setUp() {
        draftNeed = new DraftNeed(w.projects, conversations, llm, ModelCapacities.UNKNOWN, w.policy, w.clock);
        conversation = new Conversation(UUID.randomUUID(), Project.DEFAULT_ID, "Avoirs", T, T);
        conversations.save(conversation);
    }

    void exchange() {
        conversations.saveMessage(ConversationMessage.user(UUID.randomUUID(), conversation.id(), 1,
                "Les gestionnaires doivent pouvoir annuler un avoir émis par erreur.", T));
        ConversationMessage answer = ConversationMessage.agentStarted(UUID.randomUUID(), conversation.id(), 2, T)
                .withText("L'avoir est émis par le service Avoirs [1].").finished(MessageState.COMPLETE, false);
        conversations.saveMessage(answer);
        conversations.saveSources(answer.id(), List.of(new CitedSource(1, SourceKind.TICKET, "FACT-7", "Émettre un avoir")));
    }

    static EstablishedPoint point(Basis basis, String ref) {
        return new EstablishedPoint(QuestionTheme.ACTORS, "Qui annule ?", "Le gestionnaire.", basis, ref);
    }

    @Test
    void transmetEchangeEtSourcesCiteesEtEcarteLesSourcesInconnues() {
        exchange();
        llm.drafts.add(new NeedDraft("Permettre au gestionnaire d'annuler un avoir émis par erreur.",
                List.of(point(Basis.USER, null), point(Basis.SOURCE, "FACT-7"), point(Basis.SOURCE, "FACT-99"))));

        NeedDraft draft = draftNeed.draft(conversation.id());

        assertThat(draft.need()).startsWith("Permettre");
        assertThat(draft.points()).extracting(EstablishedPoint::sourceRef).containsExactly(null, "FACT-7");
        var request = llm.draftRequests.getFirst();
        assertThat(request.history()).hasSize(2);
        assertThat(request.cited()).extracting(CitedSource::ref).containsExactly("FACT-7");
    }

    @Test
    void refuseUneConversationVideEtSansAccord() {
        assertThatThrownBy(() -> draftNeed.draft(conversation.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> draftNeed.draft(UUID.randomUUID())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND));
        exchange();
        w.consents.record(new ConsentRecord(ConsentService.COPILOT, false, T));
        assertThatThrownBy(() -> draftNeed.draft(conversation.id())).isInstanceOf(DomainException.class);
        assertThat(llm.draftRequests).isEmpty();
    }

    @Test
    void unPointMalFormeEstRefuseEtLeBrouillonBorne() {
        assertThatThrownBy(() -> new EstablishedPoint(QuestionTheme.GOAL, " ", "x", Basis.USER, null))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NEED_DRAFT_INVALID));
        assertThat(point(Basis.SOURCE, " ").sourceRef()).isNull();
        assertThat(point(Basis.USER, "FACT-7").sourceRef()).isNull();
        List<EstablishedPoint> eleven = IntStream.range(0, 11).mapToObj(i -> point(Basis.USER, null)).toList();
        assertThatThrownBy(() -> new NeedDraft("x", eleven)).isInstanceOf(DomainException.class);
    }

    @Test
    void leBrouillonAnnonceLePerimetreQueLaSpecificationReprendra() {
        exchange();
        dev.specwriter.support.InMemoryPerimeters perimeters = new dev.specwriter.support.InMemoryPerimeters();
        DraftNeed withPerimeter = new DraftNeed(w.projects, conversations, llm, ModelCapacities.UNKNOWN, w.policy, w.clock,
                perimeters);
        llm.drafts.add(new NeedDraft("Permettre au gestionnaire d'annuler un avoir émis par erreur.", List.of()));
        assertThat(withPerimeter.draft(conversation.id()).perimeter()).isNull();

        UUID answer = UUID.randomUUID();
        perimeters.conversationOf.put(answer, conversation.id());
        perimeters.save(answer, StartFromConversationTest.analysis(3, "libs/a", "modules/b"));
        llm.drafts.add(new NeedDraft("Permettre au gestionnaire d'annuler un avoir émis par erreur.", List.of()));
        assertThat(withPerimeter.draft(conversation.id()).perimeter()).isEqualTo(new NeedDraft.Perimeter(2, 3));
    }
}
