package dev.specwriter.domain.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 007 : un élément désigné avec @ garde tout le reste, lien compris ; 008 : budget dans l'empreinte. */
class ProjectKnowledgeEntryTest {

    @Test
    void designeGardeLeResteEtLeLien() {
        ProjectKnowledge.Entry plain = new ProjectKnowledge.Entry(KnowledgeKind.DOC_SECTION, null, "docs:s/a.docx", 0,
                "A", null, false, "texte", "h");
        assertThat(plain.designated()).isFalse();
        ProjectKnowledge.Entry designated = plain.withLink("relates to").withDesignated();
        assertThat(designated.designated()).isTrue();
        assertThat(designated.link()).isEqualTo("relates to");
        assertThat(designated.withLink(null).designated()).isTrue();
        assertThat(designated.text()).isEqualTo("texte");
    }

    @Test
    void budgetDansLEmpreinteHorsStandardEtGardeParLesCopies() {
        ProjectKnowledge k = ProjectKnowledge.sheet("## Acteurs", null);
        assertThat(k.budget()).isEqualTo(ProjectKnowledge.MAX_TOTAL);
        ProjectKnowledge large = k.withBudget(162_000);
        assertThat(large.fingerprint()).isNotEqualTo(k.fingerprint());
        assertThat(large.withBudget(ProjectKnowledge.MAX_TOTAL).fingerprint()).isEqualTo(k.fingerprint());
        assertThat(large.withArchitecture(List.of(), List.of(), List.of()).budget()).isEqualTo(162_000);
        assertThat(large.withSubtasks(ProjectKnowledge.Subtasks.NONE).budget()).isEqualTo(162_000);
        assertThat(ProjectKnowledge.EMPTY.withBudget(162_000).fingerprint()).isEmpty();
        assertThat(k.withBudget(0).budget()).isEqualTo(ProjectKnowledge.MAX_TOTAL);
    }
}
