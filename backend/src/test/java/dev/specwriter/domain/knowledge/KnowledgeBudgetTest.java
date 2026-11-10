package dev.specwriter.domain.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** 008, research.md R2 : budget selon la limite de prompt du modèle. */
class KnowledgeBudgetTest {

    static KnowledgeBudget of(int tokens) {
        return KnowledgeBudget.forPromptTokens(OptionalInt.of(tokens));
    }

    @Test
    void inconnuOuPetitModeleGardeLesPlafondsDAujourdhui() {
        assertThat(KnowledgeBudget.forPromptTokens(OptionalInt.empty())).isEqualTo(KnowledgeBudget.STANDARD);
        assertThat(of(32_000)).isEqualTo(KnowledgeBudget.STANDARD);
        assertThat(of(0)).isEqualTo(KnowledgeBudget.STANDARD);
        assertThat(KnowledgeBudget.STANDARD.isStandard()).isTrue();
        assertThat(KnowledgeBudget.STANDARD).isEqualTo(new KnowledgeBudget(35_000, 3_000, 12_000, 8_000, 8_000, 6_000,
                12_000, 8_000, 3_000, 3_000, 1_500, 20_000, 8_000));
    }

    @Test
    void grandModeleTousLesPlafondsGrandissentEnProportion() {
        KnowledgeBudget b = of(128_000);
        assertThat(b.total()).isEqualTo(162_000);
        assertThat(b.mentionedPart()).isEqualTo(37_028);
        assertThat(b.mentionedPart()).isGreaterThan(30_000);
        assertThat(b.adrText()).isEqualTo(55_542);
        assertThat(b.subtaskText()).isEqualTo(6_942);
        assertThat(b.isStandard()).isFalse();
        assertThat(of(64_000).total()).isEqualTo(66_000);
        assertThat(of(64_000).mentionedPart()).isEqualTo(15_085);
    }

    /** 010 : place des sources et de l'historique d'une conversation, comme les autres parts. */
    @Test
    void partsDeLaConversation() {
        assertThat(KnowledgeBudget.STANDARD.conversationSources()).isEqualTo(20_000);
        assertThat(KnowledgeBudget.STANDARD.conversationHistory()).isEqualTo(8_000);
        assertThat(KnowledgeBudget.SOURCE_EXCERPT).isEqualTo(1_500);
        KnowledgeBudget b = of(128_000);
        assertThat(b.conversationSources()).isEqualTo(92_571);
        assertThat(b.conversationHistory()).isEqualTo(37_028);
    }

    @Test
    void totalPlafonne() {
        assertThat(of(2_000_000).total()).isEqualTo(KnowledgeBudget.MAX_TOTAL);
        assertThat(of(10_000_000).mentionedText()).isEqualTo(68_571);
    }

    @Test
    void partDeLaSpecificationDiscuteePriseSurLaReserveEtProportionnee() {
        assertThat(KnowledgeBudget.STANDARD.discussedSpecification()).isEqualTo(40_000);
        KnowledgeBudget large = KnowledgeBudget.forPromptTokens(java.util.OptionalInt.of(400_000));
        assertThat(large.discussedSpecification())
                .isEqualTo((int) Math.floor(40_000.0 * large.total() / KnowledgeBudget.STANDARD.total()));
        assertThat(large.discussedSpecification()).isGreaterThan(40_000);
    }
}
