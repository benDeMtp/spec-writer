package fr.kawamind.domain.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.error.DomainException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProvenanceTest {

    @Test
    void dateInconnueQuandAbsente() {
        Provenance p = Provenance.of(SourceOrigin.CONFLUENCE, null);
        assertThat(p.dateTag()).isEqualTo("inconnue");
        assertThat(p.level()).isEqualTo(Reliability.INTERPRETATION);
    }

    @Test
    void dateAuJourDeLUtilisateur() {
        // 23 h 30 UTC le 2 mars : déjà le 3 mars à Paris
        Provenance p = new Provenance(SourceOrigin.JIRA, Instant.parse("2026-03-02T23:30:00Z"), Reliability.REFERENCE);
        assertThat(p.dateTag()).isEqualTo("2026-03-03");
    }

    @Test
    void niveauNulSeulementPourFicheEtDocumentJoint() {
        assertThat(Provenance.of(SourceOrigin.SHEET, null).level()).isNull();
        assertThat(Provenance.of(SourceOrigin.ATTACHMENT, null).withLevel(Reliability.REFERENCE).level()).isNull();
        assertThatThrownBy(() -> new Provenance(SourceOrigin.ATTACHMENT, null, Reliability.REFERENCE))
                .isInstanceOf(DomainException.class);
        assertThat(Provenance.of(SourceOrigin.DOCUMENTS, null).withLevel(Reliability.TO_CONFIRM).level())
                .isEqualTo(Reliability.TO_CONFIRM);
    }
}
