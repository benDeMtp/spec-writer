package dev.specwriter.domain.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 007 : un élément désigné avec @ garde tout le reste, lien compris. */
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
}
