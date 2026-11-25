package fr.kawamind.domain.interview;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.domain.specification.Assumption;
import fr.kawamind.domain.specification.OpenQuestion;
import fr.kawamind.domain.specification.SourceDeviation;
import fr.kawamind.domain.specification.Specs;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 021, FR-025 : points à traiter d'une spécification. */
class PendingPointsTest {

    @Test
    void parSorteMoinsLesPointsTranches() {
        var cited = new SourceDeviation.Cited("jira:AV-1", null, null, null);
        var content = Specs.valid()
                .withAssumptions(List.of(new Assumption("H-01", "a", Assumption.Origin.TOOL_INFERENCE, null),
                        new Assumption("H-02", "b", Assumption.Origin.TOOL_INFERENCE, null),
                        new Assumption("H-03", "c", Assumption.Origin.TOOL_INFERENCE, null)))
                .withOpenQuestions(List.of(new OpenQuestion("Q-01", "q", null, null, null, null)))
                .withSourceDeviations(List.of(new SourceDeviation(cited, cited, "500 contre 1 000")));
        assertThat(PendingPoints.of(content, Set.of())).isEqualTo(new PendingPoints(3, 1, 1));
        PendingPoints left = PendingPoints.of(content, Set.of("H-02", "EC-01"));
        assertThat(left).isEqualTo(new PendingPoints(2, 1, 0));
        assertThat(left.total()).isEqualTo(3);
    }
}
