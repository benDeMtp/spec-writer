package dev.specwriter.domain.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 010 : règles des conversations et de leurs messages (data-model.md). */
class ConversationTest {

    static final Instant T = Instant.parse("2026-11-09T09:00:00Z");
    static final UUID C = UUID.randomUUID();

    @Test
    void nomParDefautEtRenommage() {
        String first = "Comment sont émis les avoirs aujourd'hui, et qui consomme l'événement avoir-émis côté comptabilité ?";
        Conversation c = Conversation.start(C, UUID.randomUUID(), first, T);
        assertThat(c.name()).hasSize(60).startsWith("Comment sont émis les avoirs").endsWith("…");
        assertThat(Conversation.defaultName("  Court  ")).isEqualTo("Court");
        assertThat(Conversation.defaultName("ligne un\nligne deux")).isEqualTo("ligne un ligne deux");
        assertThat(c.renamed("  Avoirs  ").name()).isEqualTo("Avoirs");
        assertThatThrownBy(() -> c.renamed(" ")).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> c.renamed("x".repeat(121))).isInstanceOf(DomainException.class);
        assertThat(c.touched(T.plusSeconds(5)).updatedAt()).isEqualTo(T.plusSeconds(5));
    }

    @Test
    void messagesBornesEtSansTexteDansToString() {
        ConversationMessage m = ConversationMessage.user(UUID.randomUUID(), C, 1, "  Où est décrit le lettrage ?  ", T);
        assertThat(m.text()).isEqualTo("Où est décrit le lettrage ?");
        assertThat(m.state()).isEqualTo(MessageState.COMPLETE);
        assertThat(m.toString()).doesNotContain("lettrage");
        assertThatThrownBy(() -> ConversationMessage.user(UUID.randomUUID(), C, 1, " ", T))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> ConversationMessage.user(UUID.randomUUID(), C, 1, "x".repeat(8_001), T))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.MESSAGE_TOO_LONG));

        ConversationMessage a = ConversationMessage.agentStarted(UUID.randomUUID(), C, 2, T);
        assertThat(a.state()).isEqualTo(MessageState.RUNNING);
        assertThat(a.text()).isEmpty();
        ConversationMessage done = a.withText("Réponse").finished(MessageState.INCOMPLETE, true);
        assertThat(done.state()).isEqualTo(MessageState.INCOMPLETE);
        assertThat(done.historyTruncated()).isTrue();
        // une réponse de l'agent est coupée à 100 000 caractères
        assertThat(a.withText("y".repeat(100_050)).text()).hasSize(100_000);
    }

    @Test
    void etapesBornees() {
        SearchStep s = new SearchStep(1, "  " + "q".repeat(400), Set.of(SourceKind.TICKET), 6);
        assertThat(s.query()).hasSize(300);
        assertThatThrownBy(() -> new SearchStep(6, "q", Set.of(), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(SearchStep.MAX_STEPS).isEqualTo(5);
    }
}
