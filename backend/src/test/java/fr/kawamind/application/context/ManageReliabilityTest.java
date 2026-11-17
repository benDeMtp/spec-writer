package fr.kawamind.application.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.knowledge.Reliability;
import fr.kawamind.domain.knowledge.SourceOrigin;
import fr.kawamind.domain.project.Project;
import fr.kawamind.support.InMemoryReliability;
import fr.kawamind.support.JiraWorld;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T040 (016) : niveaux de fiabilité par sorte de source, défauts, réglages. */
class ManageReliabilityTest {

    static final UUID P = Project.DEFAULT_ID;
    final JiraWorld w = new JiraWorld();
    final InMemoryReliability repository = new InMemoryReliability();
    final ManageReliability reliability = new ManageReliability(w.projects, repository);

    @Test
    void defautsSharePointReferenceLeResteInterpretation() {
        ManageReliability.Levels levels = reliability.get(P);
        assertThat(levels.levels()).hasSize(6).containsEntry(SourceOrigin.DOCUMENTS, Reliability.REFERENCE)
                .containsEntry(SourceOrigin.CONFLUENCE, Reliability.INTERPRETATION)
                .containsEntry(SourceOrigin.JIRA, Reliability.INTERPRETATION);
        assertThat(levels.defaults()).isEqualTo(levels.levels());
    }

    @Test
    void changementDUneSorteEtRetourAuDefaut() {
        assertThat(reliability.set(P, SourceOrigin.CONFLUENCE, Reliability.TO_CONFIRM).levels())
                .containsEntry(SourceOrigin.CONFLUENCE, Reliability.TO_CONFIRM);
        assertThat(repository.find(P).overrides()).containsOnlyKeys(SourceOrigin.CONFLUENCE);
        // un niveau égal au défaut efface le réglage
        reliability.set(P, SourceOrigin.CONFLUENCE, Reliability.INTERPRETATION);
        assertThat(repository.find(P).overrides()).isEmpty();
    }

    @Test
    void sortesNonReglablesEtProjetInconnu() {
        assertThatThrownBy(() -> reliability.set(P, SourceOrigin.SHEET, Reliability.REFERENCE))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> reliability.get(UUID.randomUUID())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
    }
}
