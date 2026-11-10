package dev.specwriter.application.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.application.conversation.ConversationHistory.Phase;
import dev.specwriter.domain.conversation.CitedSource;
import dev.specwriter.domain.conversation.ConversationMemory;
import dev.specwriter.domain.conversation.ConversationMessage;
import dev.specwriter.domain.conversation.MessageState;
import dev.specwriter.domain.conversation.SourceKind;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.knowledge.KnowledgeBudget;
import dev.specwriter.domain.port.ConversationLlm.Exchange;
import dev.specwriter.domain.port.ModelCapacities;
import dev.specwriter.domain.session.Language;
import dev.specwriter.support.FixedClock;
import dev.specwriter.support.InMemoryConversations;
import dev.specwriter.support.ScriptedConversationLlm;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 010 : mémoire de la conversation, fenêtre glissante puis compactage des échanges anciens. */
class ConversationHistoryTest {

    static final Instant T = Instant.parse("2026-11-10T09:00:00Z");

    final InMemoryConversations conversations = new InMemoryConversations();
    final ScriptedConversationLlm llm = new ScriptedConversationLlm();
    final ConversationHistory history = new ConversationHistory(conversations, llm, new FixedClock());
    final KnowledgeBudget budget = ModelCapacities.UNKNOWN.budget();
    final UUID cid = UUID.randomUUID();
    final List<Phase> phases = new ArrayList<>();

    /** {@code count} échanges question-réponse de {@code size} caractères chacun. */
    List<ConversationMessage> exchanges(int count, int size) {
        List<ConversationMessage> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(ConversationMessage.user(UUID.randomUUID(), cid, 2 * i + 1, "Q" + i + " " + "q".repeat(size), T));
            ConversationMessage answer = ConversationMessage.agentStarted(UUID.randomUUID(), cid, 2 * i + 2, T)
                    .withText("R" + i + " " + "r".repeat(size)).finished(MessageState.COMPLETE, false);
            out.add(answer);
        }
        out.forEach(conversations::saveMessage);
        return out;
    }

    ConversationHistory.Prepared prepare(List<ConversationMessage> all) {
        return history.prepare(cid, all, all.size() + 1, Language.FR, budget, phases::add);
    }

    @Test
    void sansDebordementNiResumeNiAppel() {
        ConversationHistory.Prepared p = prepare(exchanges(3, 100));
        assertThat(p.memory()).isEmpty();
        assertThat(p.exchanges()).hasSize(6);
        assertThat(p.truncated()).isFalse();
        assertThat(llm.compactRequests).isEmpty();
        assertThat(phases).isEmpty();
    }

    @Test
    void lesCitationsAnciennesDeviennentLeTitreDeLeurSource() {
        ConversationMessage q = ConversationMessage.user(UUID.randomUUID(), cid, 1, "Comment ?", T);
        ConversationMessage a = ConversationMessage.agentStarted(UUID.randomUUID(), cid, 2, T)
                .withText("Par le bus [2].").finished(MessageState.COMPLETE, false);
        conversations.saveMessage(q);
        conversations.saveMessage(a);
        conversations.saveSources(a.id(), List.of(new CitedSource(2, SourceKind.ADR, "adr/5.html", "5. Bus")));

        ConversationHistory.Prepared p = prepare(List.of(q, a));

        assertThat(p.exchanges()).extracting(Exchange::text).containsExactly("Comment ?", "Par le bus (« 5. Bus »).");
    }

    @Test
    void debordementCompacteLesAnciensJusquALaMoitieDeLaPart() {
        // 10 échanges de ~1000 caractères : 20 000 caractères pour une part de 8 000
        List<ConversationMessage> all = exchanges(10, 1_000);

        ConversationHistory.Prepared p = prepare(all);

        assertThat(phases).containsExactly(Phase.STARTED, Phase.DONE);
        // 17 000 caractères à rattraper pour des appels de 16 000 au plus : deux appels, le second
        // repart du premier résumé
        assertThat(llm.compactRequests).hasSize(2);
        assertThat(llm.compactRequests.getFirst().previous()).isEmpty();
        assertThat(llm.compactRequests.get(1).previous()).isEqualTo("Résumé 1");
        ConversationMemory memory = conversations.memory(cid).orElseThrow();
        assertThat(p.memory()).isEqualTo(memory.summary()).isEqualTo("Résumé 2");
        // la fenêtre récente tient dans la moitié de la part : le tour suivant n'aura pas à compacter
        int recent = p.exchanges().stream().mapToInt(e -> e.text().length()).sum();
        assertThat(recent).isLessThanOrEqualTo(budget.conversationHistory() / 2);
        // les messages résumés ne sont plus transmis en entier : la fenêtre reprend juste après
        int c = memory.coveredUntil();
        assertThat(p.exchanges().getFirst().text()).startsWith(c % 2 == 0 ? "Q" + c / 2 + " " : "R" + (c - 1) / 2 + " ");
        assertThat(p.truncated()).isFalse();

        // un échange de plus : il tient encore, pas de nouvel appel ; le résumé est transmis
        phases.clear();
        List<ConversationMessage> more = new ArrayList<>(all);
        more.addAll(exchangesFrom(11, 1, 100));
        ConversationHistory.Prepared next = prepare(more);
        assertThat(phases).isEmpty();
        assertThat(llm.compactRequests).hasSize(2);
        assertThat(next.memory()).isEqualTo("Résumé 2");
    }

    List<ConversationMessage> exchangesFrom(int ordinal, int count, int size) {
        List<ConversationMessage> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(ConversationMessage.user(UUID.randomUUID(), cid, ordinal + 2 * i, "Q+ " + "q".repeat(size), T));
            out.add(ConversationMessage.agentStarted(UUID.randomUUID(), cid, ordinal + 2 * i + 1, T)
                    .withText("R+ " + "r".repeat(size)).finished(MessageState.COMPLETE, false));
        }
        out.forEach(conversations::saveMessage);
        return out;
    }

    @Test
    void nouveauCompactagePartDuResumePrecedent() {
        List<ConversationMessage> all = exchanges(10, 1_000);
        prepare(all);
        List<ConversationMessage> more = new ArrayList<>(all);
        more.addAll(exchangesFrom(21, 5, 1_000));

        int before = llm.compactRequests.size();
        List<String> summarized = llm.compactRequests.stream().flatMap(r -> r.exchanges().stream())
                .map(Exchange::text).toList();
        prepare(more);

        assertThat(llm.compactRequests).hasSize(before + 1);
        var last = llm.compactRequests.getLast();
        assertThat(last.previous()).isEqualTo("Résumé " + before);
        // les messages déjà résumés ne sont pas renvoyés
        assertThat(last.exchanges()).extracting(Exchange::text).doesNotContainAnyElementsOf(summarized);
        assertThat(conversations.memory(cid).orElseThrow().summary()).isEqualTo("Résumé " + (before + 1));
    }

    @Test
    void compactageEnEchecRetombeSurLaFenetreSignalee() {
        llm.compactFailure = new DomainException(ErrorCode.LLM_UNAVAILABLE);
        ConversationHistory.Prepared p = prepare(exchanges(10, 1_000));

        assertThat(phases).containsExactly(Phase.STARTED, Phase.FAILED);
        assertThat(p.memory()).isEmpty();
        assertThat(p.truncated()).isTrue();
        assertThat(p.exchanges().stream().mapToInt(e -> e.text().length()).sum())
                .isLessThanOrEqualTo(budget.conversationHistory());
        assertThat(conversations.memory(cid)).isEmpty();
    }
}
