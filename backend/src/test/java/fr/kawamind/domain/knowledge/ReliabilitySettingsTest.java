package fr.kawamind.domain.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.error.DomainException;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T037 (016) : niveaux par sorte de source, défauts, racines Confluence (FR-015, FR-016, FR-016a). */
class ReliabilitySettingsTest {

    static final UUID P = UUID.randomUUID();

    @Test
    void defautsSharePointReferenceLeResteInterpretation() {
        ReliabilitySettings s = ReliabilitySettings.defaults(P);
        assertThat(s.level(SourceOrigin.DOCUMENTS)).isEqualTo(Reliability.REFERENCE);
        Arrays.stream(SourceOrigin.values()).filter(o -> o.adjustable() && o != SourceOrigin.DOCUMENTS)
                .forEach(o -> assertThat(s.level(o)).as(o.name()).isEqualTo(Reliability.INTERPRETATION));
        assertThat(s.level(SourceOrigin.SHEET)).isNull();
        assertThat(s.uniform()).isFalse();
    }

    @Test
    void reglageEgalAuDefautEfface() {
        ReliabilitySettings s = ReliabilitySettings.defaults(P).with(SourceOrigin.JIRA, Reliability.TO_CONFIRM);
        assertThat(s.overrides()).containsOnlyKeys(SourceOrigin.JIRA);
        assertThat(s.with(SourceOrigin.JIRA, Reliability.INTERPRETATION).overrides()).isEmpty();
        assertThat(s.with(SourceOrigin.JIRA, null).overrides()).isEmpty();
        assertThat(new ReliabilitySettings(P, Map.of(SourceOrigin.DOCUMENTS, Reliability.REFERENCE)).overrides())
                .isEmpty();
        assertThatThrownBy(() -> new ReliabilitySettings(P, Map.of(SourceOrigin.ATTACHMENT, Reliability.REFERENCE)))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void niveauxUniformes() {
        ReliabilitySettings s = ReliabilitySettings.defaults(P).with(SourceOrigin.DOCUMENTS, Reliability.INTERPRETATION);
        assertThat(s.uniform()).isTrue();
    }

    @Test
    void pageAtteinteParDeuxRacinesPrendLePlusHautNiveau() {
        ReliabilitySettings s = ReliabilitySettings.defaults(P).with(SourceOrigin.CONFLUENCE, Reliability.TO_CONFIRM);
        assertThat(s.confluence(java.util.List.of())).isEqualTo(Reliability.TO_CONFIRM);
        assertThat(s.confluence(Arrays.asList((Reliability) null))).isEqualTo(Reliability.TO_CONFIRM);
        assertThat(s.confluence(Arrays.asList(null, Reliability.REFERENCE))).isEqualTo(Reliability.REFERENCE);
        assertThat(s.confluence(Arrays.asList(Reliability.INTERPRETATION, null))).isEqualTo(Reliability.INTERPRETATION);
    }
}
