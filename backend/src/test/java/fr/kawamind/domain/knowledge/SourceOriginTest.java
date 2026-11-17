package fr.kawamind.domain.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SourceOriginTest {

    @Test
    void huitOriginesDontSixReglables() {
        assertThat(SourceOrigin.values()).hasSize(8);
        assertThat(Arrays.stream(SourceOrigin.values()).filter(SourceOrigin::adjustable)).containsExactly(
                SourceOrigin.JIRA, SourceOrigin.ANTORA, SourceOrigin.DOCUMENTS, SourceOrigin.CONFLUENCE,
                SourceOrigin.ARCHITECTURE, SourceOrigin.SPECIFICATIONS);
    }

    @Test
    void valeursTransmisesAuModele() {
        assertThat(Arrays.stream(SourceOrigin.values()).map(SourceOrigin::tag)).containsExactly("jira", "antora",
                "sharepoint", "confluence", "architecture", "specification", "fiche-generale", "document-joint");
    }

    @Test
    void ordreDesNiveaux() {
        assertThat(Reliability.REFERENCE.above(Reliability.INTERPRETATION)).isTrue();
        assertThat(Reliability.INTERPRETATION.above(Reliability.TO_CONFIRM)).isTrue();
        assertThat(Reliability.TO_CONFIRM.above(Reliability.REFERENCE)).isFalse();
        assertThat(Reliability.max(Reliability.TO_CONFIRM, Reliability.REFERENCE)).isEqualTo(Reliability.REFERENCE);
        assertThat(Reliability.max(null, Reliability.TO_CONFIRM)).isEqualTo(Reliability.TO_CONFIRM);
        assertThat(Reliability.max(Reliability.INTERPRETATION, null)).isEqualTo(Reliability.INTERPRETATION);
        assertThat(Reliability.TO_CONFIRM.tag()).isEqualTo("a-confirmer");
    }
}
