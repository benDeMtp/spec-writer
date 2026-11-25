package fr.kawamind.domain.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 021, data-model.md : préparée, en cours, terminée ; participants bornés. */
class InterviewTest {

    static final Instant T0 = Instant.parse("2026-11-20T09:00:00Z");
    static final UUID PROJECT = new UUID(0, 9);

    @Test
    void transitions() {
        Interview prepared = Interview.prepare(new UUID(0, 1), PROJECT, List.of(" Julie M. ", "Paul D.", "Julie M."), T0);
        assertThat(prepared.state()).isEqualTo(Interview.State.PREPARED);
        assertThat(prepared.participants()).containsExactly("Julie M.", "Paul D.");

        Interview started = prepared.start(T0.plusSeconds(60));
        assertThat(started.state()).isEqualTo(Interview.State.IN_PROGRESS);
        assertThat(started.startedAt()).isEqualTo(T0.plusSeconds(60));

        Interview finished = started.finish(T0.plusSeconds(120));
        assertThat(finished.state()).isEqualTo(Interview.State.FINISHED);
        assertThat(finished.finishedAt()).isEqualTo(T0.plusSeconds(120));

        assertThatThrownBy(() -> finished.start(T0)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.INTERVIEW_NOT_PREPARED));
        assertThatThrownBy(() -> prepared.finish(T0)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.INTERVIEW_NOT_IN_PROGRESS));
        assertThatThrownBy(finished::requireInProgress).isInstanceOf(DomainException.class);
        started.requireInProgress();
    }

    @Test
    void demarrageDirect() {
        Interview direct = Interview.startNow(new UUID(0, 2), PROJECT, List.of(), T0);
        assertThat(direct.state()).isEqualTo(Interview.State.IN_PROGRESS);
        assertThat(direct.startedAt()).isEqualTo(T0);
    }

    @Test
    void participantsBornes() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            many.add("P" + i);
        }
        assertThatThrownBy(() -> Interview.prepare(new UUID(0, 1), PROJECT, many, T0))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> Interview.prepare(new UUID(0, 1), PROJECT, List.of("x".repeat(121)), T0))
                .isInstanceOf(DomainException.class);
        assertThat(Interview.prepare(new UUID(0, 1), PROJECT, List.of(" ", "x".repeat(120)), T0).participants())
                .containsExactly("x".repeat(120));
    }

    @Test
    void preparationModifiable() {
        Interview prepared = Interview.prepare(new UUID(0, 1), PROJECT, List.of("A"), T0);
        assertThat(prepared.withParticipants(List.of("B")).participants()).containsExactly("B");
        assertThatThrownBy(() -> prepared.start(T0).requirePrepared()).isInstanceOf(DomainException.class);
    }
}
