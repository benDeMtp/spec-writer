package dev.specwriter.domain.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KnowledgeSourceTest {

    static final Instant SYNCED = Instant.parse("2026-11-05T08:00:00Z");
    static final Instant MARK = Instant.parse("2026-11-06T08:00:00Z");

    /** 005, research.md R4 : une réindexation de Jira seul avance son repère, pas la date de synchronisation. */
    @Test
    void repereJiraSansDateDeSynchronisation() {
        KnowledgeSource source = KnowledgeSource.empty(UUID.randomUUID()).withArchitecture("/c4/workspace.dsl", "aurora")
                .withArchitectureHash("h1").synced(null, SYNCED);

        KnowledgeSource moved = source.withJiraMark(MARK);

        assertThat(moved.jiraMark()).isEqualTo(MARK);
        assertThat(moved.syncedAt()).isEqualTo(SYNCED);
        assertThat(moved.architectureHash()).isEqualTo("h1");
        assertThat(moved.workspace()).isEqualTo("/c4/workspace.dsl");
    }
}
