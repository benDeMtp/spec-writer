package fr.kawamind.domain.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T003, T023 : délai, bilan et échéance d'une source (017, data-model.md). */
class SourceSyncDomainTest {

    static final UUID P = UUID.randomUUID();
    static final Instant T = Instant.parse("2026-11-19T10:00:00Z");
    static final Duration MINUTE = Duration.ofMinutes(1);

    @Test
    void unBilanNeCompteRienDeNegatif() {
        assertThatThrownBy(() -> new SourceChange(-1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(SourceChange.NONE.changed()).isFalse();
        assertThat(new SourceChange(0, 0, 1).changed()).isTrue();
        assertThat(new SourceChange(2, 0, 0).changed()).isTrue();
    }

    @Test
    void delaisDeLaListeFermee() {
        assertThat(SyncInterval.fromMinutes(null)).isEqualTo(SyncInterval.OFF);
        assertThat(SyncInterval.fromMinutes(15)).isEqualTo(SyncInterval.M15);
        assertThat(SyncInterval.fromMinutes(1440)).isEqualTo(SyncInterval.D1);
        assertThat(SyncInterval.H4.minutes()).isEqualTo(240);
        assertThat(SyncInterval.OFF.minutes()).isNull();
        assertThatThrownBy(() -> SyncInterval.fromMinutes(10)).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void laDureeDUneMinuteSeRegle() {
        assertThat(SyncInterval.M15.duration(MINUTE)).isEqualTo(Duration.ofMinutes(15));
        assertThat(SyncInterval.M15.duration(Duration.ofSeconds(1))).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void echeances() {
        SourceSync off = SourceSync.empty(P, "JIRA");
        assertThat(off.dueAt(MINUTE)).isEmpty();
        assertThat(off.isDue(T, MINUTE)).isFalse();

        SourceSync never = off.withInterval(SyncInterval.M30);
        assertThat(never.isDue(T, MINUTE)).isTrue();

        SourceSync ran = never.recorded(SourceSyncResult.success(T, new SourceChange(1, 0, 0)));
        assertThat(ran.dueAt(MINUTE)).contains(T.plus(Duration.ofMinutes(30)));
        assertThat(ran.isDue(T.plus(Duration.ofMinutes(29)), MINUTE)).isFalse();
        assertThat(ran.isDue(T.plus(Duration.ofMinutes(30)), MINUTE)).isTrue();
    }

    @Test
    void lEcheancePartDeLaDerniereTentativeMemeEnEchec() {
        SourceSync ok = SourceSync.empty(P, "JIRA").withInterval(SyncInterval.H1)
                .recorded(SourceSyncResult.success(T, SourceChange.NONE));
        SourceSync failed = ok.recorded(SourceSyncResult.failure(T.plusSeconds(600), "UNAUTHORIZED"));
        assertThat(failed.dueAt(MINUTE)).contains(T.plusSeconds(600).plus(Duration.ofHours(1)));
        assertThat(failed.lastSuccessAt()).isEqualTo(T);
        assertThat(failed.last().outcome()).isEqualTo(SourceSyncResult.Outcome.ERROR);
        assertThat(failed.last().errorCode()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void resultatsDUnBilan() {
        assertThat(SourceSyncResult.success(T, SourceChange.NONE).outcome()).isEqualTo(SourceSyncResult.Outcome.UNCHANGED);
        assertThat(SourceSyncResult.success(T, new SourceChange(0, 3, 0)).outcome())
                .isEqualTo(SourceSyncResult.Outcome.CHANGED);
        assertThat(SourceSyncResult.failure(T, "X").change()).isEqualTo(SourceChange.NONE);
        assertThatThrownBy(() -> SourceSyncResult.failure(T, null)).isInstanceOf(NullPointerException.class);
    }
}
