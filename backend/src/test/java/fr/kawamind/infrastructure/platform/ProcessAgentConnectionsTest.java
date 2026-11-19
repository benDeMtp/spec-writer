package fr.kawamind.infrastructure.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** T013 (015, research R4) : une connexion est vivante si son processus existe avec le même début. */
class ProcessAgentConnectionsTest {

    final ProcessAgentConnections connections = new ProcessAgentConnections();

    @Test
    void leProcessusCourantEstVivant() {
        String me = ProcessAgentConnections.identify(ProcessHandle.current()).orElseThrow();
        assertThat(connections.alive(me)).isTrue();
    }

    @Test
    void unAutreDebutOuUnPidInconnuEstFerme() {
        ProcessHandle me = ProcessHandle.current();
        long start = me.info().startInstant().orElseThrow().toEpochMilli();
        assertThat(connections.alive(me.pid() + "-" + (start + 1))).isFalse();
        assertThat(connections.alive(Long.MAX_VALUE + "-" + start)).isFalse();
    }

    @Test
    void unIdentifiantMalFormeEstFerme() {
        assertThat(connections.alive(null)).isFalse();
        assertThat(connections.alive("")).isFalse();
        assertThat(connections.alive("abc-def")).isFalse();
        assertThat(connections.alive("-12")).isFalse();
        assertThat(connections.alive("12")).isFalse();
    }
}
